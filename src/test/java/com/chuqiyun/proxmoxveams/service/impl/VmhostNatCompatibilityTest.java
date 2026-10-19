package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.chuqiyun.proxmoxveams.dto.NatOperationResult;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.NatForwardRule;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.NatForwardRuleService;
import com.chuqiyun.proxmoxveams.service.NatForwardSyncService;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import com.chuqiyun.proxmoxveams.utils.ClientPayloadUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: 星禾
 * @Description: 验证历史 NAT 端口幂等放行，同时保持新建规则策略校验。
 * @DateTime: 2026/10/19 19:53
 */
class VmhostNatCompatibilityTest {
    private static final int VM_ID = 7;
    private static final int NODE_ID = 1;
    private static final String TOKEN = "nat-test-token";
    private static final String SOURCE_IP = "127.0.0.1";
    private static final String DESTINATION_IP = "192.0.2.10";
    private static final int DESTINATION_PORT = 22;

    @TempDir
    Path temporaryDirectory;

    private VmhostServiceImpl service;
    private MasterService masterService;
    private Master node;
    private NatForwardRuleService natForwardRuleService;
    private NatForwardSyncService natForwardSyncService;
    private ConfigService configService;
    private Object previousClientMasterService;

    @BeforeEach
    void setUp() {
        if (TableInfoHelper.getTableInfo(Master.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Master.class);
        }
        previousClientMasterService = ReflectionTestUtils.getField(ClientApiUtil.class, "masterService");
        service = spy(new VmhostServiceImpl());
        masterService = mock(MasterService.class);
        natForwardRuleService = mock(NatForwardRuleService.class);
        natForwardSyncService = mock(NatForwardSyncService.class);
        configService = mock(ConfigService.class);
        node = node();

        when(masterService.getById(NODE_ID)).thenReturn(node);
        // 拒绝分支一旦意外调用 ClientApiUtil，立即失败，避免网络异常让拒绝用例误通过。
        when(masterService.getOne(any(LambdaQueryWrapper.class)))
                .thenThrow(new AssertionError("不应向 QAgent 下发被拒绝的 NAT 请求"));
        new ClientApiUtil().setMasterService(masterService);
        when(configService.getToken()).thenReturn(TOKEN);
        when(natForwardSyncService.countPortRules(VM_ID)).thenReturn(0L);
        when(natForwardSyncService.savePortRule(anyInt(), anyInt(), anyString(), anyInt(),
                anyString(), anyInt(), anyString())).thenReturn(true);
        doReturn(vm()).when(service).getById(VM_ID);
        doReturn(NODE_ID).when(service).getVmhostNodeId(VM_ID);

        ReflectionTestUtils.setField(service, "masterService", masterService);
        ReflectionTestUtils.setField(service, "natForwardRuleService", natForwardRuleService);
        ReflectionTestUtils.setField(service, "natForwardSyncService", natForwardSyncService);
        ReflectionTestUtils.setField(service, "configService", configService);
    }

    @AfterEach
    void clearClientApiMasterService() {
        ReflectionTestUtils.setField(ClientApiUtil.class, "masterService", previousClientMasterService);
    }

    @Test
    void existingMatchingRuleWithLegacyOrReservedPortIsForwarded() throws Exception {
        int[] legacyPorts = {65147, 64525, 59978, 62093, 80, 443, 7600};
        Vmhost existingVm = vm();
        existingVm.setNatnum(0);
        doReturn(existingVm).when(service).getById(VM_ID);
        when(natForwardRuleService.getOne(any(QueryWrapper.class)))
                .thenReturn(rule(legacyPorts[0], VM_ID, DESTINATION_IP, DESTINATION_PORT, "tcp"))
                .thenReturn(rule(legacyPorts[1], VM_ID, DESTINATION_IP, DESTINATION_PORT, "tcp"))
                .thenReturn(rule(legacyPorts[2], VM_ID, DESTINATION_IP, DESTINATION_PORT, "tcp"))
                .thenReturn(rule(legacyPorts[3], VM_ID, DESTINATION_IP, DESTINATION_PORT, "tcp"))
                .thenReturn(rule(legacyPorts[4], VM_ID, DESTINATION_IP, DESTINATION_PORT, "tcp"))
                .thenReturn(rule(legacyPorts[5], VM_ID, DESTINATION_IP, DESTINATION_PORT, "tcp"))
                .thenReturn(rule(legacyPorts[6], VM_ID, DESTINATION_IP, DESTINATION_PORT, "tcp"));

        try (TestQAgent agent = startQAgent()) {
            node.setControllerPort(agent.port());
            new ClientApiUtil().setMasterService(masterService);

            for (int sourcePort : legacyPorts) {
                if (sourcePort == 7600) {
                    node.setPort(7600);
                }
                NatOperationResult result = service.addVmhostNatWithResult(
                        SOURCE_IP, sourcePort, DESTINATION_IP, DESTINATION_PORT, "tcp", VM_ID);
                assertTrue(result.isSuccess(), "port=" + sourcePort + ": " + result.getMessage());
            }

            assertEquals(legacyPorts.length, agent.requestCount());
            assertTrue(agent.paths().stream().allMatch("/nat/add"::equals));
            assertTrue(agent.authorization().stream().allMatch(TOKEN::equals));
            for (int index = 0; index < legacyPorts.length; index++) {
                assertRequest(agent.requests().get(index), legacyPorts[index], "tcp");
            }
            verify(natForwardSyncService, org.mockito.Mockito.times(legacyPorts.length))
                    .savePortRule(anyInt(), anyInt(), anyString(), anyInt(), anyString(), anyInt(), anyString());
            verify(natForwardSyncService, never()).countPortRules(VM_ID);
        }
    }

    @Test
    void newRuleWithLegacyOrOutOfRangePortIsRejected() {
        when(natForwardRuleService.getOne(any(QueryWrapper.class))).thenReturn(null);

        for (int sourcePort : new int[]{65147, 64525, 59978, 62093, 80, 443}) {
            NatOperationResult result = service.addVmhostNatWithResult(
                    SOURCE_IP, sourcePort, DESTINATION_IP, DESTINATION_PORT, "tcp", VM_ID);
            assertFalse(result.isSuccess(), "port=" + sourcePort);
        }

        verify(natForwardSyncService, never()).savePortRule(anyInt(), anyInt(), anyString(), anyInt(),
                anyString(), anyInt(), anyString());
    }

    @Test
    void newRuleCannotUseNodeControllerPort() {
        node.setControllerPort(7600);
        when(natForwardRuleService.getOne(any(QueryWrapper.class))).thenReturn(null);

        NatOperationResult result = service.addVmhostNatWithResult(
                SOURCE_IP, 7600, DESTINATION_IP, DESTINATION_PORT, "tcp", VM_ID);

        assertFalse(result.isSuccess());
        verify(natForwardSyncService, never()).savePortRule(anyInt(), anyInt(), anyString(), anyInt(),
                anyString(), anyInt(), anyString());
    }

    @Test
    void changedDestinationIsRejectedEvenWhenPortWasPreviouslyUsed() {
        when(natForwardRuleService.getOne(any(QueryWrapper.class)))
                .thenReturn(rule(65147, VM_ID, "192.0.2.11", DESTINATION_PORT, "tcp"));

        NatOperationResult result = service.addVmhostNatWithResult(
                SOURCE_IP, 65147, DESTINATION_IP, DESTINATION_PORT, "tcp", VM_ID);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("占用"));
        verify(natForwardSyncService, never()).savePortRule(anyInt(), anyInt(), anyString(), anyInt(),
                anyString(), anyInt(), anyString());
    }

    @Test
    void changedVmIsRejectedEvenWhenPortWasPreviouslyUsed() {
        when(natForwardRuleService.getOne(any(QueryWrapper.class)))
                .thenReturn(rule(65147, 8, DESTINATION_IP, DESTINATION_PORT, "tcp"));

        NatOperationResult result = service.addVmhostNatWithResult(
                SOURCE_IP, 65147, DESTINATION_IP, DESTINATION_PORT, "tcp", VM_ID);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("占用"));
        verify(natForwardSyncService, never()).savePortRule(anyInt(), anyInt(), anyString(), anyInt(),
                anyString(), anyInt(), anyString());
    }

    @Test
    void changedDestinationPortIsRejectedEvenWhenPortWasPreviouslyUsed() {
        when(natForwardRuleService.getOne(any(QueryWrapper.class)))
                .thenReturn(rule(65147, VM_ID, DESTINATION_IP, 3389, "tcp"));

        NatOperationResult result = service.addVmhostNatWithResult(
                SOURCE_IP, 65147, DESTINATION_IP, DESTINATION_PORT, "tcp", VM_ID);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("占用"));
        verify(natForwardSyncService, never()).savePortRule(anyInt(), anyInt(), anyString(), anyInt(),
                anyString(), anyInt(), anyString());
    }

    @Test
    void allRequestCannotReuseTcpRecordToCreateInvalidUdpRule() {
        when(natForwardRuleService.getOne(any(QueryWrapper.class)))
                .thenReturn(rule(65147, VM_ID, DESTINATION_IP, DESTINATION_PORT, "tcp"), (NatForwardRule) null);

        NatOperationResult result = service.addVmhostNatWithResult(
                SOURCE_IP, 65147, DESTINATION_IP, DESTINATION_PORT, "all", VM_ID);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("范围") || result.getMessage().contains("保留"));
        verify(natForwardSyncService, never()).savePortRule(anyInt(), anyInt(), anyString(), anyInt(),
                anyString(), anyInt(), anyString());
    }

    @Test
    void allRequestForTwoUnchangedLegacyProtocolsIsForwarded() throws Exception {
        when(natForwardRuleService.getOne(any(QueryWrapper.class)))
                .thenReturn(rule(59978, VM_ID, DESTINATION_IP, DESTINATION_PORT, "tcp"),
                        rule(59978, VM_ID, DESTINATION_IP, DESTINATION_PORT, "udp"));

        try (TestQAgent agent = startQAgent()) {
            node.setControllerPort(agent.port());

            NatOperationResult result = service.addVmhostNatWithResult(
                    SOURCE_IP, 59978, DESTINATION_IP, DESTINATION_PORT, "all", VM_ID);

            assertTrue(result.isSuccess(), result.getMessage());
            assertEquals(2, agent.requestCount());
            assertRequest(agent.requests().get(0), 59978, "tcp");
            assertRequest(agent.requests().get(1), 59978, "udp");
        }
    }

    @Test
    void legalNewRuleIsStillAddedWhenNoLegacyRecordExists() throws Exception {
        when(natForwardRuleService.getOne(any(QueryWrapper.class))).thenReturn(null);

        try (TestQAgent agent = startQAgent()) {
            node.setControllerPort(agent.port());
            new ClientApiUtil().setMasterService(masterService);

            NatOperationResult result = service.addVmhostNatWithResult(
                    SOURCE_IP, 2000, DESTINATION_IP, DESTINATION_PORT, "tcp", VM_ID);

            assertTrue(result.isSuccess(), result.getMessage());
            assertEquals(1, agent.requestCount());
            assertTrue(agent.paths().contains("/nat/add"));
            assertTrue(agent.authorization().contains(TOKEN));
            assertRequest(agent.requests().get(0), 2000, "tcp");
            verify(natForwardSyncService).savePortRule(NODE_ID, VM_ID, SOURCE_IP, 2000,
                    DESTINATION_IP, DESTINATION_PORT, "tcp");
        }
    }

    @Test
    void legalNewRuleIsNotBlockedByAnUnrelatedLegacyRecord() throws Exception {
        when(natForwardRuleService.getOne(any(QueryWrapper.class)))
                .thenReturn(rule(65147, VM_ID, DESTINATION_IP, DESTINATION_PORT, "tcp"),
                        (NatForwardRule) null);

        try (TestQAgent agent = startQAgent()) {
            node.setControllerPort(agent.port());
            new ClientApiUtil().setMasterService(masterService);

            NatOperationResult oldResult = service.addVmhostNatWithResult(
                    SOURCE_IP, 65147, DESTINATION_IP, DESTINATION_PORT, "tcp", VM_ID);
            NatOperationResult newResult = service.addVmhostNatWithResult(
                    SOURCE_IP, 2000, DESTINATION_IP, DESTINATION_PORT, "tcp", VM_ID);

            assertTrue(oldResult.isSuccess(), oldResult.getMessage());
            assertTrue(newResult.isSuccess(), newResult.getMessage());
            assertEquals(2, agent.requestCount());
            assertRequest(agent.requests().get(0), 65147, "tcp");
            assertRequest(agent.requests().get(1), 2000, "tcp");
            verify(natForwardSyncService).savePortRule(NODE_ID, VM_ID, SOURCE_IP, 2000,
                    DESTINATION_IP, DESTINATION_PORT, "tcp");
        }
    }

    private TestQAgent startQAgent() throws Exception {
        Path keystore = temporaryDirectory.resolve("qagent-test.p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool").toString();
        Process process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "qagent-test",
                "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=127.0.0.1",
                "-ext", "SAN=ip:127.0.0.1", "-validity", "2", "-storetype", "PKCS12",
                "-keystore", keystore.toString(), "-storepass", "changeit", "-keypass", "changeit",
                "-noprompt").redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor() == 0, output);

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(keystore)) {
            keyStore.load(input, "changeit".toCharArray());
        }
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, "changeit".toCharArray());
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

        X509Certificate certificate = (X509Certificate) keyStore.getCertificate("qagent-test");
        String pem = "-----BEGIN CERTIFICATE-----\n"
                + java.util.Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(certificate.getEncoded())
                + "\n-----END CERTIFICATE-----\n";
        node.setNodePublicKey(pem);
        doReturn(node).when(masterService).getOne(any(LambdaQueryWrapper.class));

        HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        AtomicInteger requestCount = new AtomicInteger();
        List<String> paths = new CopyOnWriteArrayList<>();
        List<String> authorization = new CopyOnWriteArrayList<>();
        List<JSONObject> requests = new CopyOnWriteArrayList<>();
        server.createContext("/", exchange -> {
            requestCount.incrementAndGet();
            paths.add(exchange.getRequestURI().getPath());
            authorization.add(exchange.getRequestHeaders().getFirst("Authorization"));
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String plain = ClientPayloadUtil.decrypt(TOKEN, body, exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("X-QimenIDC-Timestamp"),
                    exchange.getRequestHeaders().getFirst("X-QimenIDC-Nonce"));
            requests.add(JSONObject.parseObject(plain));
            String response = ClientPayloadUtil.encrypt(TOKEN, "{\"code\":200}", exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("X-QimenIDC-Timestamp"),
                    exchange.getRequestHeaders().getFirst("X-QimenIDC-Nonce"));
            byte[] payload = new JSONObject().fluentPut("_encrypted", response)
                    .toJSONString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        });
        server.start();
        return new TestQAgent(server, requestCount, paths, authorization, requests);
    }

    private void assertRequest(JSONObject request, int sourcePort, String protocol) {
        assertNotNull(request);
        assertEquals(VM_ID, request.getIntValue("vm"));
        assertEquals(SOURCE_IP, request.getString("source_ip"));
        assertEquals(sourcePort, request.getIntValue("source_port"));
        assertEquals(DESTINATION_IP, request.getString("destination_ip"));
        assertEquals(DESTINATION_PORT, request.getIntValue("destination_port"));
        assertEquals(protocol, request.getString("protocol"));
    }

    private Master node() {
        Master value = new Master();
        value.setId(NODE_ID);
        value.setHost(SOURCE_IP);
        value.setPort(8006);
        value.setControllerPort(17600);
        value.setNaton(1);
        return value;
    }

    private Vmhost vm() {
        Vmhost vm = new Vmhost();
        vm.setId(VM_ID);
        vm.setNodeid(NODE_ID);
        vm.setDeleteState(0);
        vm.setNatnum(20);
        vm.setIpList(Arrays.asList(DESTINATION_IP));
        return vm;
    }

    private NatForwardRule rule(int sourcePort, int hostId, String destinationIp,
                                int destinationPort, String protocol) {
        NatForwardRule rule = new NatForwardRule();
        rule.setNodeId(NODE_ID);
        rule.setHostId(hostId);
        rule.setSourceIp(SOURCE_IP);
        rule.setSourcePort(sourcePort);
        rule.setDestinationIp(destinationIp);
        rule.setDestinationPort(destinationPort);
        rule.setProtocol(protocol);
        return rule;
    }

    private record TestQAgent(HttpsServer server, AtomicInteger requestCounter,
                              List<String> paths, List<String> authorization,
                              List<JSONObject> requests) implements AutoCloseable {
        int port() {
            return server.getAddress().getPort();
        }

        int requestCount() {
            return requestCounter.get();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
