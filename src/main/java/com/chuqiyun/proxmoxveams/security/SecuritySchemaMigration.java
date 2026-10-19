package com.chuqiyun.proxmoxveams.security;

import com.chuqiyun.proxmoxveams.utils.SqlUpdateLoader;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 校验实际结构，修复历史版本已记账但安全升级 SQL 被跳过的数据库。
@Service
public class SecuritySchemaMigration {
    private static final Pattern ADD_COLUMNS = Pattern.compile(
            "(?is)^alter\\s+table\\s+(sysuser|master|agent_trust)\\s+add\\s+(.+?);?$");
    private final JdbcTemplate jdbc;

    public SecuritySchemaMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void migrate() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            String lock = "qimenidc_security_" + Integer.toHexString(String.valueOf(connection.getCatalog()).hashCode());
            try (PreparedStatement statement = connection.prepareStatement("select get_lock(?, 60)")) {
                statement.setString(1, lock);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) {
                        throw new IllegalStateException("无法取得安全数据库升级锁");
                    }
                }
            }
            try {
                SqlUpdateLoader loader = new SqlUpdateLoader();
                for (SqlUpdateLoader.BuildSqlStep step : loader.getOrderedBuildSqlSteps()) {
                    if (List.of("1.3.4_4", "1.3.4_5", "1.3.4_6").contains(step.getBuildVersion())) {
                        for (String sql : step.getSqlList()) {
                            apply(connection, sql);
                        }
                    }
                }
                apply(connection, "alter table agent_trust add member_id varchar(64) not null default 'legacy' after cluster_id");
                try (Statement statement = connection.createStatement()) {
                    if (!hasIndex(connection, "uk_agent_trust_member")) {
                        statement.execute("alter table agent_trust add unique index uk_agent_trust_member "
                                + "(cluster_id, member_id, source, node_id, address)");
                    }
                    if (hasIndex(connection, "uk_agent_trust_cluster_addr")) {
                        statement.execute("alter table agent_trust drop index uk_agent_trust_cluster_addr");
                    }
                    statement.execute("create table if not exists sys_passkey_challenge ("
                            + "challenge varchar(64) character set ascii collate ascii_bin primary key, type varchar(24) not null, "
                            + "user_uuid varchar(64) null, expires_at bigint not null, "
                            + "index idx_passkey_challenge_expiry (expires_at))");
                }
            } catch (java.io.IOException e) {
                throw new IllegalStateException("读取安全数据库升级配置失败", e);
            } finally {
                try (PreparedStatement statement = connection.prepareStatement("select release_lock(?)")) {
                    statement.setString(1, lock);
                    statement.execute();
                }
            }
            return null;
        });
    }

    private boolean hasIndex(Connection connection, String name) throws java.sql.SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "select count(*) from information_schema.statistics "
                        + "where table_schema = database() and table_name = 'agent_trust' and index_name = ?")) {
            query.setString(1, name);
            try (ResultSet result = query.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }

    private void apply(Connection connection, String sql) throws java.sql.SQLException {
        Matcher matcher = ADD_COLUMNS.matcher(sql.trim());
        try (Statement statement = connection.createStatement()) {
            if (!matcher.matches()) {
                statement.execute(sql);
                return;
            }
            String table = matcher.group(1);
            for (String definition : matcher.group(2).replaceFirst(";\\s*$", "").split("(?i),\\s*add\\s+")) {
                String column = definition.trim().split("\\s+", 2)[0];
                try (PreparedStatement query = connection.prepareStatement(
                        "select count(*) from information_schema.columns "
                                + "where table_schema = database() and table_name = ? and column_name = ?")) {
                    query.setString(1, table);
                    query.setString(2, column);
                    try (ResultSet result = query.executeQuery()) {
                        if (result.next() && result.getInt(1) == 0) {
                            statement.execute("alter table " + table + " add " + definition.trim());
                        }
                    }
                }
            }
        }
    }
}
