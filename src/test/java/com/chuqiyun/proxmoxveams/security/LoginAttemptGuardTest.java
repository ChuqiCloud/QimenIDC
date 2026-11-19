package com.chuqiyun.proxmoxveams.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptGuardTest {
    private LoginAttemptGuard guard;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:login_guard_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        guard = new LoginAttemptGuard(jdbc, new DataSourceTransactionManager(dataSource));
        guard.ensureSchema();
    }

    @Test
    void knownAccountFailuresAreIsolatedByAccountAndIp() {
        for (int i = 0; i < LoginAttemptGuard.MAX_FAILURES; i++) {
            guard.recordFailure("admin", "192.0.2.20", true);
        }

        assertTrue(guard.isLocked("admin", "192.0.2.20", true));
        assertFalse(guard.isLocked("other", "192.0.2.20", true));
    }

    @Test
    void unknownAccountFailuresLockTheSourceIpAndSuccessClearsIt() {
        for (int i = 0; i < LoginAttemptGuard.MAX_FAILURES; i++) {
            guard.recordFailure("missing", "192.0.2.21", false);
        }

        assertTrue(guard.isLocked("missing", "192.0.2.21", false));
        assertTrue(guard.isLocked("admin", "192.0.2.21", true));
        guard.recordSuccess("admin", "192.0.2.21", true);
        assertFalse(guard.isLocked("admin", "192.0.2.21", true));
    }
}
