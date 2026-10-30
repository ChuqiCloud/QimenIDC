package com.chuqiyun.proxmoxveams.service;

import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @Author: 星禾
 * @Description: 使用数据库会话锁串行执行迁移，进程退出后锁自动释放
 * @DateTime: 2026/10/30 11:44
 */
@Component
public class MigrationExecutionLock {
    @Resource
    private DataSource dataSource;
    private final ThreadLocal<Connection> connection = new ThreadLocal<>();

    public boolean runLocked(Runnable action) {
        try (Connection session = dataSource.getConnection()) {
            if (!queryLock(session, "SELECT GET_LOCK(CONCAT(DATABASE(), ':vm-migration'), 0)")) {
                return false;
            }
            connection.set(session);
            try {
                action.run();
                return true;
            } finally {
                connection.remove();
                queryLock(session, "SELECT RELEASE_LOCK(CONCAT(DATABASE(), ':vm-migration'))");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("迁移数据库执行锁不可用", e);
        }
    }

    public void assertHeld() {
        Connection session = connection.get();
        try {
            if (session == null || !queryLock(session,
                    "SELECT IS_USED_LOCK(CONCAT(DATABASE(), ':vm-migration')) = CONNECTION_ID()")) {
                throw new IllegalStateException("迁移执行锁已丢失，停止执行后续步骤");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("迁移数据库执行锁不可用", e);
        }
    }

    public boolean isHeldByCurrentThread() {
        if (connection.get() == null) {
            return false;
        }
        assertHeld();
        return true;
    }

    private boolean queryLock(Connection session, String sql) throws SQLException {
        try (PreparedStatement statement = session.prepareStatement(sql)) {
            statement.setQueryTimeout(10);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getInt(1) == 1 && !rows.wasNull();
            }
        }
    }
}
