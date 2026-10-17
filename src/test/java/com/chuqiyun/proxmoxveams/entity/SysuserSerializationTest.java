package com.chuqiyun.proxmoxveams.entity;

import com.alibaba.fastjson2.JSON;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @Author: 星禾
 * @Description: User credentials must stay absent from both JSON serializers
 * @DateTime: 2026/10/17 16:01
 */
class SysuserSerializationTest {
    @Test
    void nestedResponsesDoNotExposePasswordTotpSecretOrReplayState() throws Exception {
        Sysuser user = new Sysuser();
        user.setUsername("admin");
        user.setPassword("password-hash");
        user.setTotpSecret("QIDENC:totp-secret");
        user.setTotpLastStep(123L);
        Object response = ResponseResult.ok(Map.of("users", List.of(user)));

        for (String json : List.of(new ObjectMapper().writeValueAsString(response), JSON.toJSONString(response))) {
            assertTrue(json.contains("admin"));
            assertFalse(json.contains("password"));
            assertFalse(json.contains("totpSecret"));
            assertFalse(json.contains("totpLastStep"));
            assertFalse(json.contains("QIDENC:totp-secret"));
        }
        assertFalse(user.toString().contains("password-hash"));
        assertFalse(user.toString().contains("QIDENC:totp-secret"));
    }
}
