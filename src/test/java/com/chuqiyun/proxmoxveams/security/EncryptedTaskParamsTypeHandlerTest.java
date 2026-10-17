package com.chuqiyun.proxmoxveams.security;

import com.chuqiyun.proxmoxveams.entity.Task;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EncryptedTaskParamsTypeHandlerTest {
    private final EncryptedTaskParamsTypeHandler handler = new EncryptedTaskParamsTypeHandler();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void jdbcWriteKeepsStandardJsonAndDoesNotMutateTaskParams() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        Map<Object, Object> params = new LinkedHashMap<>();
        params.put("newPassword", "task-secret");
        params.put(7, "numeric-key");
        params.put("values", Arrays.asList(null, true, 8));
        params.put("nested", Map.of("ssh_password", "node-secret", "vmid", 12));

        handler.setNonNullParameter(ps, 2, params, JdbcType.OTHER);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(ps).setString(org.mockito.ArgumentMatchers.eq(2), json.capture());
        JsonNode stored = objectMapper.readTree(json.getValue());
        assertTrue(stored.isObject());
        assertTrue(stored.get("newPassword").asText().startsWith("QIDENC:1:"));
        assertEquals("numeric-key", stored.get("7").asText());
        assertTrue(stored.get("values").get(0).isNull());
        assertFalse(json.getValue().contains("task-secret"));
        assertFalse(json.getValue().contains("node-secret"));
        assertEquals("task-secret", params.get("newPassword"));

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("params")).thenReturn(json.getValue());
        Map<Object, Object> restored = handler.getNullableResult(rs, "params");
        assertEquals("task-secret", restored.get("newPassword"));
        assertEquals("node-secret", ((Map<?, ?>) restored.get("nested")).get("ssh_password"));
        assertEquals("numeric-key", restored.get("7"));
    }

    @Test
    void readsLegacyJsonAndHandlesSqlNullFromEveryJdbcOverload() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        CallableStatement cs = mock(CallableStatement.class);
        when(rs.getString(1)).thenReturn("{\"newPassword\":\"old-secret\",\"count\":2}");
        when(rs.getString("params")).thenReturn(null);
        when(cs.getString(1)).thenReturn(null);

        assertEquals("old-secret", handler.getNullableResult(rs, 1).get("newPassword"));
        assertNull(handler.getNullableResult(rs, "params"));
        assertNull(handler.getNullableResult(cs, 1));
        assertNull(TaskPasswordValues.encrypt(null));
        assertNull(TaskPasswordValues.decrypt(null));
    }

    @Test
    void failsClosedOnMalformedJsonOrCiphertext() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("params")).thenReturn("{\"newPassword\":\"QIDENC:1:corrupt\"}");
        assertThrows(SQLException.class, () -> handler.getNullableResult(rs, "params"));

        when(rs.getString("params")).thenReturn("[]");
        assertThrows(SQLException.class, () -> handler.getNullableResult(rs, "params"));
    }

    @Test
    void taskResponsesAndToStringOmitExecutionParamsButPreserveProgress() throws Exception {
        Task task = new Task();
        task.setId(19);
        task.setStatus(2);
        task.setParams(Map.of("newPassword", "task-secret", "ordinary", "internal",
                "stage", "running", "progress", 60,
                "nested", Map.of("token", "private-token", "label", "disk")));

        JsonNode response = objectMapper.readTree(objectMapper.writeValueAsString(task));
        assertTrue(response.has("params"));
        assertEquals("internal", response.get("params").get("ordinary").asText());
        assertEquals("running", response.get("params").get("stage").asText());
        assertEquals(60, response.get("params").get("progress").asInt());
        assertFalse(response.get("params").has("newPassword"));
        assertFalse(response.get("params").get("nested").has("token"));
        assertEquals(19, response.get("id").asInt());
        assertEquals(2, response.get("status").asInt());
        JsonNode fastjsonResponse = objectMapper.readTree(com.alibaba.fastjson2.JSON.toJSONString(task));
        assertTrue(fastjsonResponse.has("params"));
        assertEquals("internal", fastjsonResponse.get("params").get("ordinary").asText());
        assertEquals(60, fastjsonResponse.get("params").get("progress").asInt());
        assertFalse(fastjsonResponse.get("params").has("newPassword"));
        assertFalse(fastjsonResponse.get("params").get("nested").has("token"));
        assertFalse(task.toString().contains("task-secret"));
        assertEquals("task-secret", task.getParams().get("newPassword"));
    }

    @Test
    void apiSanitizerReturnsCopyAndKeepsOrdinaryNestedProgress() {
        Map<Object, Object> params = new LinkedHashMap<>();
        params.put("stage", "running");
        params.put("progress", 60);
        params.put("password", "secret");
        params.put("nested", new LinkedHashMap<>(Map.of(
                "step", "disk", "token", "private-token")));
        Task task = new Task();
        task.setParams(params);

        Map<Object, Object> sanitized = task.getParamsForApi();
        assertEquals("running", sanitized.get("stage"));
        assertEquals(60, sanitized.get("progress"));
        assertFalse(sanitized.containsKey("password"));
        assertFalse(((Map<?, ?>) sanitized.get("nested")).containsKey("token"));
        assertEquals("secret", task.getParams().get("password"));
        assertTrue(((Map<?, ?>) task.getParams().get("nested")).containsKey("token"));
        assertFalse(sanitized == task.getParams());
    }
}
