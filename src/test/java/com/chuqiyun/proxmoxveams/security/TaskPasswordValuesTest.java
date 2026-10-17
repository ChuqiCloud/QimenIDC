package com.chuqiyun.proxmoxveams.security;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskPasswordValuesTest {
    @Test
    void recursivelyEncryptsSensitiveValuesAndKeepsOrdinaryValues() {
        Map<Object, Object> source = new LinkedHashMap<>();
        source.put("password", "plain-password");
        source.put("new_password", "new-secret");
        source.put("ordinary", "visible");
        source.put(42, "numeric-key");
        source.put("nested", Map.of("ssh-password", "ssh-secret", "name", "vm"));
        source.put("list", Arrays.asList(Map.of("vncPassword", "vnc-secret"), null, "item"));
        source.put("nullValue", null);
        source.put("rootPassword", null);
        source.put("user_password", Arrays.asList("first-secret", null, "second-secret"));

        Map<Object, Object> encrypted = TaskPasswordValues.encrypt(source);
        assertTrue(((String) encrypted.get("password")).startsWith("QIDENC:1:"));
        assertTrue(((String) encrypted.get("new_password")).startsWith("QIDENC:1:"));
        assertEquals("visible", encrypted.get("ordinary"));
        assertEquals("numeric-key", encrypted.get(42));
        assertEquals("vm", ((Map<?, ?>) encrypted.get("nested")).get("name"));
        assertTrue(((String) ((Map<?, ?>) encrypted.get("nested")).get("ssh-password"))
                .startsWith("QIDENC:1:"));
        assertNull(encrypted.get("nullValue"));
        assertNull(encrypted.get("rootPassword"));
        assertTrue(((String) ((List<?>) encrypted.get("user_password")).get(0)).startsWith("QIDENC:1:"));

        Map<Object, Object> decrypted = TaskPasswordValues.decrypt(encrypted);
        assertEquals(source, decrypted);
    }

    @Test
    void encryptionIsIdempotentAndRecognizesHistoricalSpellings() {
        Map<Object, Object> source = new LinkedHashMap<>();
        source.put("SSH_PASSWORD", "ssh");
        source.put("passwd", "passwd");
        source.put("pve-password", "pve");

        Map<Object, Object> once = TaskPasswordValues.encrypt(source);
        Map<Object, Object> twice = TaskPasswordValues.encrypt(once);

        assertEquals(once, twice);
        assertEquals(source, TaskPasswordValues.decrypt(twice));
        assertTrue(TaskPasswordValues.isPasswordKey("newPassword"));
        assertFalse(TaskPasswordValues.isPasswordKey("username"));
        assertThrows(IllegalArgumentException.class,
                () -> TaskPasswordValues.encrypt(Map.of("password", "QIDENC:1:corrupt")));
    }

    @Test
    void storageEncryptionRefreshesCiphertextForEveryWrite() {
        Map<Object, Object> source = new LinkedHashMap<>();
        source.put("password", "secret");

        Map<Object, Object> first = TaskPasswordValues.encryptForStorage(source);
        Map<Object, Object> second = TaskPasswordValues.encryptForStorage(source);

        assertTrue(first.get("password").toString().startsWith("QIDENC:1:"));
        assertTrue(second.get("password").toString().startsWith("QIDENC:1:"));
        assertFalse(first.get("password").equals(second.get("password")));
        assertEquals(source, TaskPasswordValues.decrypt(second));

        Map<Object, Object> literalPrefix = Map.of("password", "QIDENC:literal-user-password");
        assertEquals(literalPrefix, TaskPasswordValues.decrypt(TaskPasswordValues.encryptForStorage(literalPrefix)));
    }

    @Test
    void jsonRoundTripProducesValidObjectAndHandlerDecryptsLegacyPlaintext() {
        Map<Object, Object> source = new LinkedHashMap<>();
        source.put("password", "secret");
        source.put("count", 2);
        String json = JSON.toJSONString(TaskPasswordValues.encrypt(source));

        Map<?, ?> stored = JSON.parseObject(json, Map.class);
        assertTrue(((String) stored.get("password")).startsWith("QIDENC:1:"));
        assertEquals(source, TaskPasswordValues.decrypt(stored));

        Map<?, ?> legacy = JSON.parseObject("{\"password\":\"old\",\"ordinary\":true}", Map.class);
        assertEquals("old", TaskPasswordValues.decrypt(legacy).get("password"));
        assertEquals(Boolean.TRUE, TaskPasswordValues.decrypt(legacy).get("ordinary"));
    }
}
