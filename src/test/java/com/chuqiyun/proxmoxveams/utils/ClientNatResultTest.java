package com.chuqiyun.proxmoxveams.utils;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.dto.NatOperationResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @Author: 星禾
 * @Description: 验证节点 NAT 失败详情在响应解密后仍能传给业务层。
 */
class ClientNatResultTest {
    @Test
    void encryptedBusinessFailurePreservesNodeReason() {
        String path = "/nat/add";
        String token = "nat-test-token";
        String timestamp = "1700000000";
        String nonce = "nat-failure-test";
        String message = "NAT rule synchronization failed: Source port is reserved or outside the allowed range";
        String plain = new JSONObject().fluentPut("code", 500).fluentPut("message", message).toJSONString();
        String encrypted = ClientPayloadUtil.encrypt(token, plain, "POST", path, timestamp, nonce);
        String body = new JSONObject().fluentPut("_encrypted", encrypted).toJSONString();

        JSONObject response = ReflectionTestUtils.invokeMethod(ClientApiUtil.class, "parseResponse",
                body, token, "POST", path, timestamp, nonce);
        NatOperationResult result = ClientApiUtil.natOperationResult(response, "添加");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains(message));
        assertTrue(result.getMessage().contains("code=500"));
        assertFalse(result.getMessage().contains("端口是否已占用"));
    }

    @Test
    void networkOrDecryptionFailureIsNotReportedAsPortConflict() {
        NatOperationResult result = ClientApiUtil.natOperationResult(null, "添加");
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("未收到有效响应"));
        assertFalse(result.getMessage().contains("占用"));
    }

    @Test
    void missingCodeOrMessageCannotBeMistakenForSuccess() {
        NatOperationResult result = ClientApiUtil.natOperationResult(new JSONObject(), "删除");
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("节点未提供错误详情"));
    }

    @Test
    void successfulResponseKeepsExistingSuccessContract() {
        assertTrue(ClientApiUtil.natOperationResult(
                new JSONObject().fluentPut("code", 200).fluentPut("data", new JSONObject()), "添加").isSuccess());
    }

    @Test
    void failureDoesNotDumpResponseDataOrUnboundedControlCharacters() {
        JSONObject response = new JSONObject().fluentPut("code", 500)
                .fluentPut("message", "iptables failed\n\r\u001b" + "x".repeat(2000))
                .fluentPut("data", new JSONObject().fluentPut("privateValue", "not-for-display"));
        String message = ClientApiUtil.natOperationResult(response, "添加").getMessage();
        assertFalse(message.contains("\n"));
        assertFalse(message.contains("\r"));
        assertFalse(message.contains("\u001b"));
        assertFalse(message.contains("not-for-display"));
        assertTrue(message.length() < 1200);
    }
}
