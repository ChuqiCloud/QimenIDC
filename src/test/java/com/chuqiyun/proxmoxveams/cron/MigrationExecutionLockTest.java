package com.chuqiyun.proxmoxveams.cron;

import com.chuqiyun.proxmoxveams.service.MigrationExecutionLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MigrationExecutionLockTest {
    private static final String ACQUIRE = "SELECT GET_LOCK(CONCAT(DATABASE(), ':vm-migration'), 0)";
    private static final String RELEASE = "SELECT RELEASE_LOCK(CONCAT(DATABASE(), ':vm-migration'))";
    private static final String ASSERT = "SELECT IS_USED_LOCK(CONCAT(DATABASE(), ':vm-migration')) = CONNECTION_ID()";
    private MigrationExecutionLock lock;
    private Connection session;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        session = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(session);
        lock = new MigrationExecutionLock();
        ReflectionTestUtils.setField(lock, "dataSource", dataSource);
    }

    @Test
    void doesNotRunWorkflowWhenAnotherManagerOwnsExecutionLock() throws Exception {
        result(ACQUIRE, 0);
        AtomicBoolean ran = new AtomicBoolean();

        assertFalse(lock.runLocked(() -> ran.set(true)));

        assertFalse(ran.get());
        verify(session).close();
    }

    @Test
    void failsClosedWhenExecutionLockCannotBeAcquired() throws Exception {
        when(session.prepareStatement(ACQUIRE)).thenThrow(new java.sql.SQLException("database unavailable"));
        AtomicBoolean ran = new AtomicBoolean();

        assertThrows(IllegalStateException.class, () -> lock.runLocked(() -> ran.set(true)));

        assertFalse(ran.get());
        verify(session).close();
    }

    @Test
    void refusesFurtherWorkflowStepsAfterOwnershipIsLost() throws Exception {
        result(ACQUIRE, 1);
        result(RELEASE, 1);
        result(ASSERT, 0);

        assertTrue(lock.runLocked(() -> assertThrows(IllegalStateException.class, lock::assertHeld)));

        verify(session).prepareStatement(RELEASE);
        verify(session).close();
        assertThrows(IllegalStateException.class, lock::assertHeld);
    }

    @Test
    void releasesDatabaseLockAndConnectionWhenWorkflowThrows() throws Exception {
        result(ACQUIRE, 1);
        result(RELEASE, 1);

        assertThrows(IllegalArgumentException.class, () -> lock.runLocked(() -> {
            throw new IllegalArgumentException("migration failed");
        }));

        verify(session).prepareStatement(RELEASE);
        verify(session).close();
        assertThrows(IllegalStateException.class, lock::assertHeld);
    }

    private void result(String sql, int value) throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(session.prepareStatement(sql)).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        when(rows.getInt(1)).thenReturn(value);
        when(rows.wasNull()).thenReturn(false);
    }
}
