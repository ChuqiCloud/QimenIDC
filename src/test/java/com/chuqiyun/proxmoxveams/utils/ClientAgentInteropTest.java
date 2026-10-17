package com.chuqiyun.proxmoxveams.utils;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 主控与被控端负载协议互通测试。
 * <p>
 * 这里用Java复刻被控端(Security.py)的加解密逻辑，验证两侧协议完全一致，
 * 任何一侧改动magic、AAD拼装方式或字段顺序都会让用例失败。
 */
class ClientAgentInteropTest {

    private static final String TOKEN = "testtoken";

    private String agentEncrypt(String plain, String method, String path, String ts, String nonce) throws Exception {
        byte[] key = MessageDigest.getInstance("SHA-256").digest(TOKEN.getBytes(StandardCharsets.UTF_8));
        byte[] iv = new byte[12];
        for (int i = 0; i < iv.length; i++) {
            iv[i] = (byte) i;
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD((method + "|" + path + "|" + ts + "|" + nonce).getBytes(StandardCharsets.UTF_8));
        byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] result = new byte[22 + cipherText.length];
        System.arraycopy("QIMENIDC-1".getBytes(StandardCharsets.UTF_8), 0, result, 0, 10);
        System.arraycopy(iv, 0, result, 10, 12);
        System.arraycopy(cipherText, 0, result, 22, cipherText.length);
        return Base64.getEncoder().encodeToString(result);
    }

    @Test
    void decryptsAgentResponseWithFixedIv() throws Exception {
        String plain = "{\"code\":200,\"message\":\"success\",\"data\":{}}";
        String encoded = agentEncrypt(plain, "POST", "/writeFile", "1700000000", "nonce-1");
        assertEquals(plain, ClientPayloadUtil.decrypt(TOKEN, encoded, "POST", "/writeFile", "1700000000", "nonce-1"));
        assertNull(ClientPayloadUtil.decrypt(TOKEN, encoded, "POST", "/writeFile", "1700000001", "nonce-1"));
    }

    @Test
    void masterCipherTextFollowsAgentLayout() {
        String encoded = ClientPayloadUtil.encrypt(TOKEN, "{\"a\":1}", "POST", "/nat/add", "1700000000", "nonce-2");
        byte[] blob = Base64.getDecoder().decode(encoded);
        assertEquals(10 + 12 + 16 + "{\"a\":1}".length(), blob.length);
        assertEquals("QIMENIDC-1", new String(blob, 0, 10, StandardCharsets.UTF_8));
    }

    @Test
    void plainPayloadMatchesAgentCompatMagic() {
        JSONObject payload = new JSONObject();
        payload.put("path", "/etc/network");
        String raw = ClientPayloadUtil.buildPlainPayload(payload);
        JSONObject wrapper = JSONObject.parseObject(raw);
        assertEquals("QIMEN-PLAIN-1.0.", wrapper.getString("_magic"));
        assertEquals("/etc/network", wrapper.getJSONObject("_data").getString("path"));
    }
}
