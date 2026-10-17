package com.chuqiyun.proxmoxveams.entity;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 节点密码和 PVE 登录票据不得通过实体、分页或嵌套响应泄露。
 *
 * @author 星禾
 */
class MasterSerializationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void jacksonOmitsNodeCredentialsInNestedApiResponse() throws Exception {
        Master master = masterWithCredentials();
        Page<Master> page = new Page<>(1, 20);
        page.setRecords(Collections.singletonList(master));
        page.setTotal(1);

        String response = objectMapper.writeValueAsString(ResponseResult.ok(Map.of("nodes", page)));
        assertCredentialsAbsent(response);
        assertTrue(response.contains("node-one"));
        assertEquals("pve-secret", master.getPassword());
        assertEquals("csrf-secret", master.getCsrfToken());
        assertEquals("ticket-secret", master.getTicket());
    }

    @Test
    void fastjsonOmitsNodeCredentialsFromObjectAndMapConversion() {
        Master master = masterWithCredentials();

        assertCredentialsAbsent(JSON.toJSONString(master));
        assertCredentialsAbsent(JSON.toJSONString(JSON.toJSON(master)));
        assertTrue(JSON.toJSONString(master).contains("node-one"));
    }

    @Test
    void submittedPasswordRemainsReadableByInternalCode() throws Exception {
        String request = "{\"name\":\"node-one\",\"password\":\"new-secret\"}";

        Master jacksonMaster = objectMapper.readValue(request, Master.class);
        Master fastjsonMaster = JSON.parseObject(request, Master.class);

        assertEquals("new-secret", jacksonMaster.getPassword());
        assertEquals("new-secret", fastjsonMaster.getPassword());
        JsonNode response = objectMapper.readTree(objectMapper.writeValueAsString(jacksonMaster));
        assertFalse(response.has("password"));
    }

    @Test
    void toStringOmitsNodeCredentials() {
        assertCredentialsAbsent(masterWithCredentials().toString());
    }

    private Master masterWithCredentials() {
        Master master = new Master();
        master.setId(1);
        master.setName("node-one");
        master.setPassword("pve-secret");
        master.setCsrfToken("csrf-secret");
        master.setTicket("ticket-secret");
        return master;
    }

    private void assertCredentialsAbsent(String output) {
        assertFalse(output.contains("pve-secret"));
        assertFalse(output.contains("csrf-secret"));
        assertFalse(output.contains("ticket-secret"));
        assertFalse(output.contains("\"password\""));
        assertFalse(output.contains("\"csrfToken\""));
        assertFalse(output.contains("\"ticket\""));
    }
}
