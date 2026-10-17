package com.chuqiyun.proxmoxveams.security;

import com.chuqiyun.proxmoxveams.constant.TaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.aop.support.AopUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoredCredentialMigrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private StoredCredentialMigration migration;

    @BeforeEach
    void openDatabase() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        jdbc = context.getBean(JdbcTemplate.class);
        migration = context.getBean(StoredCredentialMigration.class);
        assertTrue(AopUtils.isAopProxy(migration));
        createSchema();
    }

    @AfterEach
    void closeDatabase() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void migratesAllCredentialColumnsAcrossPagesAndIsIdempotent() {
        for (int id = 1; id <= 205; id++) {
            jdbc.update("insert into master (id, password, csrf_token, ticket) values (?, ?, ?, ?)",
                    id, "pve-" + id, "csrf-" + id, "ticket-" + id);
            jdbc.update("insert into vmhost (id, password) values (?, ?)", id, "vm-" + id);
            jdbc.update("insert into vncinfo (id, password) values (?, ?)", id, "vnc-" + id);
            jdbc.update("insert into task (id, type, status, params) values (?, ?, ?, ?)", id,
                    TaskType.UPDATE_IO_SYSTEM_DISK, id % 5, "{\"password\":\"task-" + id + "\",\"progress\":7}");
            jdbc.update("insert into system_log (id, request_body, response_body) values (?, ?, ?)", id,
                    "{\"password\":\"request-" + id + "\"}", "{\"ticket\":\"response-" + id + "\"}");
            jdbc.update("update system_log set query_string = ?, content = ?, exception = ?, business_message = ? where id = ?",
                    "%73sh_password=request-" + id + "&keep=ordinary-" + id,
                    "{\"password\":\"content-" + id + "\",\"keep\":\"ordinary-content-" + id + "\"}",
                    "IllegalStateException: password=exception-" + id,
                    "password=business-" + id, id);
        }

        migration.migrate();

        for (int id = 1; id <= 205; id++) {
            assertEncrypted("master", "password", id, "pve-" + id);
            assertEncrypted("master", "csrf_token", id, "csrf-" + id);
            assertEncrypted("master", "ticket", id, "ticket-" + id);
            assertEncrypted("vmhost", "password", id, "vm-" + id);
            assertEncrypted("vncinfo", "password", id, "vnc-" + id);
            String task = jdbc.queryForObject("select params from task where id = ?", String.class, id);
            assertFalse(task.contains("task-" + id));
            assertTrue(task.contains("QIDENC:1:"));
            assertEquals("{\"password\":\"***\"}", jdbc.queryForObject("select request_body from system_log where id = ?", String.class, id));
            assertEquals("{\"ticket\":\"***\"}", jdbc.queryForObject("select response_body from system_log where id = ?", String.class, id));
            assertEquals("%73sh_password=***&keep=ordinary-" + id,
                    jdbc.queryForObject("select query_string from system_log where id = ?", String.class, id));
            assertEquals("{\"password\":\"***\",\"keep\":\"ordinary-content-" + id + "\"}",
                    jdbc.queryForObject("select content from system_log where id = ?", String.class, id));
            assertEquals(StoredLogRedactor.SENSITIVE_TEXT_PLACEHOLDER,
                    jdbc.queryForObject("select exception from system_log where id = ?", String.class, id));
            assertEquals(StoredLogRedactor.SENSITIVE_TEXT_PLACEHOLDER,
                    jdbc.queryForObject("select business_message from system_log where id = ?", String.class, id));
        }
        assertEquals(205, jdbc.queryForObject("select count(*) from master where password like 'QIDENC:1:%'", Integer.class));

        Map<String, List<Map<String, Object>>> before = snapshotCredentialColumns();
        migration.migrate();
        assertEquals(before, snapshotCredentialColumns());
    }

    @Test
    void recursivelyMigratesEveryTaskTypeAndPasswordKeyAndKeepsOrdinaryValues() throws Exception {
        String taskJson = "{" +
                "\"password\":\"plain-password\"," +
                "\"passwd\":\"plain-passwd\"," +
                "\"newPassword\":\"plain-new\"," +
                "\"ssh_password\":\"plain-ssh\"," +
                "\"pve-password\":\"plain-pve\"," +
                "\"cipassword\":\"plain-ci\"," +
                "\"vncPassword\":\"plain-vnc\"," +
                "\"rootPassword\":\"plain-root\"," +
                "\"admin-password\":\"plain-admin\"," +
                "\"user_password\":\"plain-user\"," +
                "\"nested\":{" +
                "\"password\":\"nested-password\"," +
                "\"keep\":\"ordinary\"}," +
                "\"items\":[{" +
                "\"vnc_password\":\"list-password\"}]," +
                "\"passwordList\":{\"password\":[\"array-one\",\"array-two\"]}," +
                "\"keep\":\"ordinary-value\"}";
        List<Integer> taskTypes = new ArrayList<>();
        for (Field field : TaskType.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == Integer.class) {
                taskTypes.add((Integer) field.get(null));
            }
        }
        assertTrue(taskTypes.size() > 10);
        for (int index = 0; index < taskTypes.size(); index++) {
            jdbc.update("insert into task (id, type, status, params) values (?, ?, ?, ?)",
                    index + 1, taskTypes.get(index), index % 5, taskJson);
        }

        migration.migrate();

        for (String stored : jdbc.queryForList("select params from task", String.class)) {
            assertFalse(stored.contains("plain-password"));
            assertFalse(stored.contains("nested-password"));
            assertFalse(stored.contains("list-password"));
            assertTrue(stored.contains("ordinary-value"));
            assertTrue(stored.contains("ordinary"));
            Map<?, ?> values = JSON.readValue(stored, Map.class);
            assertDecryptedTaskValue(values, "password", "plain-password");
            assertDecryptedTaskValue(values, "passwd", "plain-passwd");
            assertDecryptedTaskValue(values, "newPassword", "plain-new");
            assertDecryptedTaskValue(values, "ssh_password", "plain-ssh");
            assertDecryptedTaskValue(values, "pve-password", "plain-pve");
            assertDecryptedTaskValue(values, "cipassword", "plain-ci");
            assertDecryptedTaskValue(values, "vncPassword", "plain-vnc");
            assertDecryptedTaskValue(values, "rootPassword", "plain-root");
            assertDecryptedTaskValue(values, "admin-password", "plain-admin");
            assertDecryptedTaskValue(values, "user_password", "plain-user");
            Map<?, ?> nested = (Map<?, ?>) values.get("nested");
            assertEquals("nested-password", StoredPasswordCipher.decrypt((String) nested.get("password")));
            List<?> items = (List<?>) values.get("items");
            assertEquals("list-password", StoredPasswordCipher.decrypt((String) ((Map<?, ?>) items.get(0)).get("vnc_password")));
            Map<Object, Object> decrypted = TaskPasswordValues.decrypt(values);
            assertEquals(JSON.readValue(taskJson, Map.class), decrypted);
        }
    }

    @Test
    void redactsHistoricalSystemLogBodies() {
        jdbc.update("insert into system_log (id, query_string, request_body, response_body, content, exception, business_message) values (?, ?, ?, ?, ?, ?, ?)", 1,
                "%73sh_password=request-secret&keep=ordinary&token=token-secret",
                "{\"hostId\":7,\"password\":\"request-secret\",\"nested\":{\"token\":\"token-secret\"}}",
                "password=response-secret",
                "{\"name\":\"kept-content\",\"password\":\"content-secret\"}",
                "RuntimeException: password=exception-secret",
                "password=business-secret");
        jdbc.update("insert into system_log (id, query_string, request_body, response_body, content, exception, business_message) values (?, ?, ?, ?, ?, ?, ?)", 2,
                "keep=ordinary&bad=%ZZ",
                "[broken-json password=broken-secret", "{\"name\":\"kept\"}",
                "[broken-json password=content-broken", "[broken password=exception-broken", "ordinary business message");

        migration.migrate();

        String request = jdbc.queryForObject("select request_body from system_log where id = 1", String.class);
        String response = jdbc.queryForObject("select response_body from system_log where id = 1", String.class);
        assertFalse(request.contains("request-secret"));
        assertFalse(request.contains("token-secret"));
        assertFalse(response.contains("response-secret"));
        assertEquals(StoredLogRedactor.SENSITIVE_TEXT_PLACEHOLDER,
                jdbc.queryForObject("select response_body from system_log where id = 1", String.class));
        assertEquals("%73sh_password=***&keep=ordinary&token=***",
                jdbc.queryForObject("select query_string from system_log where id = 1", String.class));
        assertEquals("{\"name\":\"kept-content\",\"password\":\"***\"}",
                jdbc.queryForObject("select content from system_log where id = 1", String.class));
        assertEquals(StoredLogRedactor.SENSITIVE_TEXT_PLACEHOLDER,
                jdbc.queryForObject("select exception from system_log where id = 1", String.class));
        assertEquals(StoredLogRedactor.SENSITIVE_TEXT_PLACEHOLDER,
                jdbc.queryForObject("select business_message from system_log where id = 1", String.class));
        assertEquals(StoredLogRedactor.INVALID_JSON_PLACEHOLDER,
                jdbc.queryForObject("select request_body from system_log where id = 2", String.class));
        assertEquals(StoredLogRedactor.INVALID_QUERY_PLACEHOLDER,
                jdbc.queryForObject("select query_string from system_log where id = 2", String.class));
        assertEquals(StoredLogRedactor.INVALID_JSON_PLACEHOLDER,
                jdbc.queryForObject("select content from system_log where id = 2", String.class));
        assertEquals(StoredLogRedactor.INVALID_JSON_PLACEHOLDER,
                jdbc.queryForObject("select exception from system_log where id = 2", String.class));
        assertEquals("ordinary business message",
                jdbc.queryForObject("select business_message from system_log where id = 2", String.class));
    }

    @Test
    void redactsEncodedQueryKeysAndPreservesOrdinaryParametersExactly() {
        String original = "%73sh_password=ssh-secret&pve%2Dpassword=pve-secret&keep=a%20b"
                + "&keep=second&hostId=7&flag&CSRF%5Ftoken=csrf-secret";
        jdbc.update("insert into system_log (id, query_string, content, exception, business_message) values (?, ?, ?, ?, ?)",
                1, original, "ordinary content", "ordinary exception", "ordinary business message");

        migration.migrate();

        String migrated = jdbc.queryForObject("select query_string from system_log where id = 1", String.class);
        assertEquals("%73sh_password=***&pve%2Dpassword=***&keep=a%20b&keep=second&hostId=7&flag&CSRF%5Ftoken=***",
                migrated);
        assertFalse(migrated.contains("ssh-secret"));
        assertFalse(migrated.contains("pve-secret"));
        assertFalse(migrated.contains("csrf-secret"));
        assertEquals("ordinary content", jdbc.queryForObject("select content from system_log where id = 1", String.class));
        assertEquals("ordinary exception", jdbc.queryForObject("select exception from system_log where id = 1", String.class));
        assertEquals("ordinary business message", jdbc.queryForObject("select business_message from system_log where id = 1", String.class));
        Map<String, List<Map<String, Object>>> before = snapshotCredentialColumns();
        migration.migrate();
        assertEquals(before, snapshotCredentialColumns());
    }

    @Test
    void badEncryptedValueRollsBackEarlierRowsAndDoesNotExposeSecret() {
        jdbc.update("insert into master (id, password) values (?, ?)", 1, "must-rollback");
        jdbc.update("insert into vmhost (id, password) values (?, ?)", 1, "vm-must-rollback");
        jdbc.update("insert into vncinfo (id, password) values (?, ?)", 1, "vnc-must-rollback");
        jdbc.update("insert into task (id, params) values (?, ?)", 1, "{\"password\":\"task-must-rollback\"}");
        jdbc.update("insert into task (id, params) values (?, ?)", 2, "{\"nested\":{\"password\":\"QIDENC:1:corrupted\"}}");
        jdbc.update("insert into system_log (id, request_body, query_string, content, exception, business_message) values (?, ?, ?, ?, ?, ?)", 1,
                "{\"password\":\"log-must-rollback\"}", "password=log-query-must-rollback",
                "{\"password\":\"log-content-must-rollback\"}", "password=log-exception-must-rollback",
                "password=log-message-must-rollback");
        Map<String, List<Map<String, Object>>> before = snapshotCredentialColumns();

        IllegalStateException exception = assertThrows(IllegalStateException.class, migration::migrate);
        assertSanitizedFailure(exception);

        assertEquals("must-rollback", jdbc.queryForObject("select password from master where id = 1", String.class));
        assertEquals(before, snapshotCredentialColumns());
    }

    @Test
    void badCredentialInSecondPageRollsBackEveryEarlierRow() {
        for (int id = 1; id <= 204; id++) {
            jdbc.update("insert into master (id, password) values (?, ?)", id, "must-rollback-" + id);
        }
        jdbc.update("insert into master (id, password) values (?, ?)", 205, "QIDENC:1:corrupted");
        Map<String, List<Map<String, Object>>> before = snapshotCredentialColumns();

        IllegalStateException exception = assertThrows(IllegalStateException.class, migration::migrate);

        assertSanitizedFailure(exception);
        assertEquals(before, snapshotCredentialColumns());
        assertEquals("must-rollback-1", jdbc.queryForObject("select password from master where id = 1", String.class));
        assertEquals("must-rollback-204", jdbc.queryForObject("select password from master where id = 204", String.class));
    }

    private void assertSanitizedFailure(IllegalStateException exception) {
        assertEquals("历史凭据迁移失败，已回滚；请检查数据库结构及密文完整性", exception.getMessage());
        assertFalse(exception.getMessage().contains("must-rollback"));
        assertFalse(exception.getMessage().contains("corrupted"));
        assertNull(exception.getCause());
        assertEquals(0, exception.getSuppressed().length);
    }

    private void assertDecryptedTaskValue(Map<?, ?> values, String key, String expected) {
        String stored = (String) values.get(key);
        assertTrue(StoredPasswordCipher.isEncrypted(stored));
        assertEquals(expected, StoredPasswordCipher.decrypt(stored));
    }

    private void assertEncrypted(String table, String column, int id, String expected) {
        String stored = jdbc.queryForObject("select " + column + " from " + table + " where id = ?", String.class, id);
        assertTrue(StoredPasswordCipher.isEncrypted(stored));
        assertNotEquals(expected, stored);
        assertEquals(expected, StoredPasswordCipher.decrypt(stored));
    }

    private Map<String, List<Map<String, Object>>> snapshotCredentialColumns() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : List.of("master", "vmhost", "vncinfo", "task", "system_log")) {
            result.put(table, jdbc.queryForList("select * from " + table + " order by id"));
        }
        return result;
    }

    private void createSchema() {
        jdbc.execute("create table master (id int primary key, password varchar(1024), csrf_token varchar(1024), ticket varchar(1024))");
        jdbc.execute("create table vmhost (id int primary key, password varchar(1024))");
        jdbc.execute("create table vncinfo (id int primary key, password varchar(1024))");
        jdbc.execute("create table task (id int primary key, type int, status int, params varchar(16384))");
        jdbc.execute("create table system_log (id int primary key, query_string varchar(16384), request_body varchar(16384), response_body varchar(16384), content varchar(16384), exception varchar(16384), business_message varchar(16384))");
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    "jdbc:h2:mem:migration_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
            dataSource.setDriverClassName("org.h2.Driver");
            return dataSource;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        StoredCredentialMigration storedCredentialMigration(JdbcTemplate jdbcTemplate) {
            return new StoredCredentialMigration(jdbcTemplate);
        }
    }
}
