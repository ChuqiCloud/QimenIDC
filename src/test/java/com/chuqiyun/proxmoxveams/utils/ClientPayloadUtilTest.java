package com.chuqiyun.proxmoxveams.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientPayloadUtilTest {

    private static final String TOKEN = "testtoken";

    @Test
    void encryptsAndDecryptsRoundTrip() {
        String plain = "{\"path\":\"/var/lib/vz/snippets\"}";
        String encoded = ClientPayloadUtil.encrypt(TOKEN, plain, "POST", "/writeFile", "123", "abc");
        assertEquals(plain, ClientPayloadUtil.decrypt(TOKEN, encoded, "POST", "/writeFile", "123", "abc"));
    }

    @Test
    void keepsProtocolHeaderStable() {
        String encoded = ClientPayloadUtil.encrypt(TOKEN, "{}", "POST", "/writeFile", "1", "n");
        byte[] blob = Base64.getDecoder().decode(encoded);
        assertEquals("QIMENIDC-1", new String(blob, 0, 10, StandardCharsets.UTF_8));
    }

    @Test
    void rejectsTamperedPayloadAndWrongAad() {
        String encoded = ClientPayloadUtil.encrypt(TOKEN, "{\"a\":1}", "POST", "/writeFile", "123", "abc");
        byte[] blob = Base64.getDecoder().decode(encoded);
        blob[blob.length - 5] ^= 0x7f;
        assertNull(ClientPayloadUtil.decrypt(TOKEN, Base64.getEncoder().encodeToString(blob),
                "POST", "/writeFile", "123", "abc"));
        assertNull(ClientPayloadUtil.decrypt(TOKEN, encoded, "POST", "/readFile", "123", "abc"));
        assertNull(ClientPayloadUtil.decrypt(TOKEN, encoded, "POST", "/writeFile", "124", "abc"));
        assertNull(ClientPayloadUtil.decrypt("other-token", encoded, "POST", "/writeFile", "123", "abc"));
    }

    /**
     * 由被控端（Python）加密的负载，主控必须能解开，反之亦然，
     * 任何一侧协议变更都会让这个用例失败。
     */
    @Test
    void decryptsPayloadFromAgentSide() {
        String encoded = "UUlNRU5JREMtMR5kqPzZExPGoFPpBEP3mb/cEQj0h5HfZAj5mBrqZ5IZCw==";
        assertEquals("hello", ClientPayloadUtil.decrypt(TOKEN, encoded, "POST", "/writeFile", "123", "abc"));
    }

    @Test
    void buildsPlainPayloadForLegacyAgent() {
        JSONObject payload = new JSONObject();
        payload.put("path", "/home/images");
        JSONObject wrapper = JSON.parseObject(ClientPayloadUtil.buildPlainPayload(payload));
        assertEquals("QIMEN-PLAIN-1.0.", wrapper.getString("_magic"));
        assertEquals("/home/images", wrapper.getJSONObject("_data").getString("path"));
    }

    @Test
    void filtersUndeclaredFields() {
        JSONObject params = new JSONObject();
        params.put("path", "/var/lib/vz/snippets");
        params.put("filename", "a.yaml");
        params.put("content", "c");
        params.put("ssh_password", "leaked");
        assertTrue(ControllerApiSchema.getAllowedFields("http://1.1.1.1:7600/writeFile").contains("content"));
        assertEquals(3, ControllerApiSchema.getAllowedFields("http://1.1.1.1:7600/writeFile").size());
        assertNotNull(ControllerApiSchema.getAllowedFields("http://1.1.1.1:7600/security-group/apply"));
    }
}
