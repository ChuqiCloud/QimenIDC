package com.chuqiyun.proxmoxveams.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * @author 星禾
 * 启动时迁移历史凭据。直接读取原始列，避免经过实体解密后重复加密。
 */
@Service
public class StoredCredentialMigration {
    private static final int BATCH_SIZE = 200;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcTemplate jdbc;

    public StoredCredentialMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 在数据库结构升级后、任何节点预热或任务执行前调用。
     * 同一事务内完成迁移，失败则回滚，且错误中不携带原始列或数据库异常内容。
     */
    @Transactional
    public void migrate() {
        try {
            migrateTable("master", List.of("password", "csrf_token", "ticket"),
                    StoredCredentialMigration::encryptLegacy);
            migrateTable("vmhost", List.of("password"), StoredCredentialMigration::encryptLegacy);
            migrateTable("vncinfo", List.of("password"), StoredCredentialMigration::encryptLegacy);
            migrateTable("task", List.of("params"), StoredCredentialMigration::encryptTask);
            migrateTable("system_log", List.of("request_body", "response_body", "content", "exception",
                    "business_message"), StoredLogRedactor::redact);
            migrateTable("system_log", List.of("query_string"), StoredLogRedactor::redactQueryString);
        } catch (RuntimeException e) {
            // SQL 错误和 JSON 解析错误可能包含原文，不向日志或启动异常附加 cause。
            throw new IllegalStateException("历史凭据迁移失败，已回滚；请检查数据库结构及密文完整性");
        }
    }

    private void migrateTable(String table, List<String> columns, UnaryOperator<String> transform) {
        long lastId = -1;
        String selectedColumns = String.join(", ", columns);
        while (true) {
            List<StoredRow> rows = jdbc.query("select id, " + selectedColumns + " from " + table
                            + " where id > ? order by id limit ? for update",
                    (rs, rowNum) -> {
                        List<String> values = new ArrayList<>(columns.size());
                        for (String column : columns) {
                            values.add(rs.getString(column));
                        }
                        return new StoredRow(rs.getLong("id"), values);
                    }, lastId, BATCH_SIZE);
            if (rows.isEmpty()) {
                return;
            }
            for (StoredRow row : rows) {
                for (int index = 0; index < columns.size(); index++) {
                    String original = row.values().get(index);
                    String migrated = transform.apply(original);
                    if (!Objects.equals(original, migrated)) {
                        jdbc.update("update " + table + " set " + columns.get(index) + " = ? where id = ?",
                                migrated, row.id());
                    }
                }
                lastId = row.id();
            }
        }
    }

    private static String encryptLegacy(String value) {
        if (StoredPasswordCipher.isEncrypted(value)) {
            StoredPasswordCipher.decrypt(value);
            return value;
        }
        return StoredPasswordCipher.encrypt(value);
    }

    private static String encryptTask(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            Map<?, ?> original = JSON.readValue(value, Map.class);
            Map<Object, Object> encrypted = TaskPasswordValues.encrypt(original);
            return Objects.equals(original, encrypted) ? value : JSON.writeValueAsString(encrypted);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("历史任务参数格式无效");
        }
    }

    private record StoredRow(long id, List<String> values) {
    }
}
