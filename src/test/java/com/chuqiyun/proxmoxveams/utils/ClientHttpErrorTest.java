package com.chuqiyun.proxmoxveams.utils;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * @Author: 星禾
 * @Description: 用真实HTTP错误验证节点拒绝原因传递及凭据脱敏。
 */
class ClientHttpErrorTest {
    private static final String TOKEN = "node-authorization-not-for-display";
    private HttpServer server;
    private Object previousMasterService;
    private Logger logger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void startServer() throws IOException {
        if (TableInfoHelper.getTableInfo(Master.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Master.class);
        }
        previousMasterService = ReflectionTestUtils.getField(ClientApiUtil.class, "masterService");
        ReflectionTestUtils.setField(ClientApiUtil.class, "masterService", mock(MasterService.class));
        logger = (Logger) LoggerFactory.getLogger(ClientApiUtil.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        logger.detachAppender(logs);
        logs.stop();
        ReflectionTestUtils.setField(ClientApiUtil.class, "masterService", previousMasterService);
    }

    private JSONObject requestError(int status, String body) {
        return requestError(status, body, new HashMap<>());
    }

    private JSONObject requestError(int status, String body, Map<String, Object> params) {
        return requestError("/migrationAction/channel", status, body, params);
    }

    private JSONObject requestError(String path, int status, String body, Map<String, Object> params) {
        server.createContext(path, exchange -> write(exchange, status, body));
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + path;
        return ClientApiUtil.postControllerApi(url, params, TOKEN);
    }

    private static void write(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    @Test
    void preservesFastApiMissingSourceArchiveReasonInsteadOfReportingNoResponse() {
        String detail = "task does not have a completed, available source archive";
        JSONObject response = requestError(400, new JSONObject().fluentPut("detail", detail).toJSONString());

        assertNotNull(response);
        assertEquals(400, response.getInteger("code"));
        assertEquals("HTTP 400：" + detail, response.getString("message"));
        assertTrue(response.getJSONObject("data").isEmpty());
    }

    @Test
    void preservesRetiredEndpointStatusAndDropsUnrelatedResponseData() {
        JSONObject body = new JSONObject().fluentPut("code", 410)
                .fluentPut("message", "This QAgent endpoint has been retired for security reasons")
                .fluentPut("data", new JSONObject().fluentPut("token", "secret-response-data"));
        JSONObject response = requestError(410, body.toJSONString());

        assertEquals(410, response.getInteger("code"));
        assertTrue(response.getString("message").contains("retired for security reasons"));
        assertFalse(response.toJSONString().contains("secret-response-data"));
        assertTrue(logs.list.stream().noneMatch(log -> log.getFormattedMessage().contains("secret-response-data")));
    }

    @Test
    void httpFailureCannotBeChangedToSuccessByResponseBody() {
        JSONObject response = requestError(503, "{\"code\":200,\"message\":\"unavailable\"}");

        assertEquals(503, response.getInteger("code"));
        assertTrue(response.getString("message").contains("HTTP 503"));
    }

    @Test
    void decryptsAuthenticatedHttpErrorWithTheOriginalRequestBinding() {
        server.createContext("/migrationAction/channel", exchange -> {
            String timestamp = exchange.getRequestHeaders().getFirst("X-QimenIDC-Timestamp");
            String nonce = exchange.getRequestHeaders().getFirst("X-QimenIDC-Nonce");
            String encrypted = ClientPayloadUtil.encrypt(TOKEN,
                    "{\"code\":403,\"message\":\"channel is unavailable\"}",
                    "POST", "/migrationAction/channel", timestamp, nonce);
            write(exchange, 403, new JSONObject().fluentPut("_encrypted", encrypted).toJSONString());
        });
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/migrationAction/channel";

        JSONObject response = ClientApiUtil.postControllerApi(url, new HashMap<>(), TOKEN);

        assertEquals(403, response.getInteger("code"));
        assertEquals("HTTP 403：channel is unavailable", response.getString("message"));
    }

    @Test
    void validationErrorsDoNotExposeInputOrContext() {
        String body = "{\"detail\":[{\"loc\":[\"body\",\"source_token\"],"
                + "\"msg\":\"field required\",\"input\":\"secret-input\","
                + "\"ctx\":{\"password\":\"secret-context\"}}]}";
        JSONObject response = requestError(422, body);

        assertEquals(422, response.getInteger("code"));
        assertEquals("HTTP 422：field required", response.getString("message"));
        assertFalse(response.toJSONString().contains("secret-input"));
        assertFalse(response.toJSONString().contains("secret-context"));
    }

    @Test
    void secretsAndControlCharactersAreRemovedAndErrorLengthIsBounded() {
        Map<String, Object> params = new HashMap<>();
        params.put("source_token", "transfer-token-secret");
        params.put("source_url", "https://source.example/migrationTransfer/channel-id-secret");
        String detail = "request rejected " + TOKEN + " transfer-token-secret "
                + params.get("source_url") + "\n\r\u001b"
                + "x".repeat(2000);
        JSONObject response = requestError("/migrationAction/run", 400,
                new JSONObject().fluentPut("detail", detail).toJSONString(), params);

        String message = response.getString("message");
        assertFalse(message.contains(TOKEN));
        assertFalse(message.contains("transfer-token-secret"));
        assertFalse(message.contains("channel-id-secret"));
        assertFalse(message.contains("\n"));
        assertFalse(message.contains("\r"));
        assertFalse(message.contains("\u001b"));
        assertTrue(message.length() < 1100);
        assertTrue(logs.list.stream().noneMatch(log -> log.getFormattedMessage().contains(TOKEN)
                || log.getFormattedMessage().contains("transfer-token-secret")
                || log.getFormattedMessage().contains("channel-id-secret")));
    }

    @Test
    void transferChannelIdentifiersAreNotEchoed() {
        Map<String, Object> params = new HashMap<>();
        params.put("channel_id", "private-channel-identifier");
        JSONObject response = requestError(400,
                "{\"detail\":\"channel not found: private-channel-identifier\"}", params);

        assertEquals(400, response.getInteger("code"));
        assertFalse(response.toJSONString().contains("private-channel-identifier"));
    }

    @Test
    void unknownSecretAssignmentsAreNotEchoed() {
        JSONObject response = requestError(400,
                "{\"detail\":\"failed source_token=unknown-credential password=unknown-password\"}");

        assertEquals(400, response.getInteger("code"));
        assertFalse(response.toJSONString().contains("unknown-credential"));
        assertFalse(response.toJSONString().contains("unknown-password"));
        assertTrue(logs.list.stream().noneMatch(log -> log.getFormattedMessage().contains("unknown-credential")
                || log.getFormattedMessage().contains("unknown-password")));
    }

    @Test
    void invalidJsonOrHtmlDoesNotHideHttpStatusOrEchoTheBody() {
        JSONObject response = requestError(502, "<html>proxy error password=do-not-echo</html>");

        assertEquals(502, response.getInteger("code"));
        assertTrue(response.getString("message").contains("HTTP 502"));
        assertFalse(response.toJSONString().contains("do-not-echo"));
    }
}
