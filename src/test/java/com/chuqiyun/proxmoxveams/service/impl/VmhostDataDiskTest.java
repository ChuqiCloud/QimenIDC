package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: 星禾
 * @Description: 验证新建数据盘的 PVE 分配参数和数据库同步时机
 * @DateTime: 2026/10/24 16:33
 */
class VmhostDataDiskTest {
    private static final long HOST_ID = 834L;
    private static final int NODE_ID = 13;
    private static final String CONFIG_PATH = "/api2/json/nodes/pve-test/qemu/108/config";
    private static SSLContext serverSslContext;

    @TempDir
    static Path temporaryDirectory;

    private VmhostServiceImpl service;
    private Vmhost vm;
    private HttpsServer server;
    private SSLSocketFactory previousSocketFactory;
    private HostnameVerifier previousHostnameVerifier;
    private final List<JSONObject> writes = new CopyOnWriteArrayList<>();
    private int writeStatus;

    @BeforeAll
    static void createServerCertificate() throws Exception {
        Path keystorePath = temporaryDirectory.resolve("pve-test.p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool").toString();
        Process process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "pve-test",
                "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=127.0.0.1",
                "-validity", "2", "-storetype", "PKCS12", "-keystore", keystorePath.toString(),
                "-storepass", "changeit", "-keypass", "changeit", "-noprompt")
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(keystorePath)) {
            keyStore.load(input, "changeit".toCharArray());
        }
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, "changeit".toCharArray());
        serverSslContext = SSLContext.getInstance("TLS");
        serverSslContext.init(keyManagers.getKeyManagers(), null, null);
    }

    @BeforeEach
    void setUp() throws Exception {
        previousSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
        previousHostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
        writeStatus = 200;
        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverSslContext));
        server.createContext(CONFIG_PATH, exchange -> {
            String response;
            int status;
            if ("GET".equals(exchange.getRequestMethod())) {
                response = "{\"data\":{\"scsi0\":\"local-lvm:vm-108-disk-0\","
                        + "\"scsi1\":\"local-lvm:vm-108-disk-1\"}}";
                status = 200;
            } else if ("PUT".equals(exchange.getRequestMethod())) {
                writes.add(JSONObject.parseObject(new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8)));
                status = writeStatus;
                response = status == 200 ? "{\"data\":null}" : "{\"errors\":{\"scsi2\":\"allocation failed\"}}";
            } else {
                status = 405;
                response = "{}";
            }
            byte[] payload = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, payload.length);
            try (var output = exchange.getResponseBody()) {
                output.write(payload);
            }
        });
        server.start();

        vm = new Vmhost();
        vm.setId((int) HOST_ID);
        vm.setNodeid(NODE_ID);
        vm.setVmid(108);
        vm.setStatus(0);
        vm.setDeleteState(0);
        vm.setStorage("local-lvm");
        vm.setDataDisk(new LinkedHashMap<>(Map.of("scsi1", Map.of("size", 5, "storage", "local-lvm"))));

        Master node = new Master();
        node.setId(NODE_ID);
        node.setHost("127.0.0.1");
        node.setPort(server.getAddress().getPort());
        node.setNodeName("pve-test");
        MasterService masterService = mock(MasterService.class);
        when(masterService.getById(NODE_ID)).thenReturn(node);
        when(masterService.isNodeOnline(NODE_ID)).thenReturn(true);
        when(masterService.getMasterCookieMap(NODE_ID)).thenReturn(new HashMap<>(Map.of(
                "cookie", "PVEAuthCookie=test-ticket", "CSRFPreventionToken", "test-csrf")));
        service = spy(new VmhostServiceImpl());
        ReflectionTestUtils.setField(service, "masterService", masterService);
        doReturn(vm).when(service).getById(HOST_ID);
        doAnswer(invocation -> {
            assertEquals(1, writes.size(), "PVE 接受新磁盘之后才能同步数据库");
            return true;
        }).when(service).updateById(any(Vmhost.class));
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        HttpsURLConnection.setDefaultSSLSocketFactory(previousSocketFactory);
        HttpsURLConnection.setDefaultHostnameVerifier(previousHostnameVerifier);
    }

    @Test
    void allocateDiskUsesIntegerGiBAndPreservesExistingMetadata() {
        UnifiedResultDto<Object> result = service.addVmDataDisk(HOST_ID, 10, "fast-ssd");

        assertEquals(UnifiedResultCode.SUCCESS, result.getResultCode());
        assertEquals(1, writes.size());
        assertEquals(Map.of("scsi2", "fast-ssd:10,discard=on"), writes.get(0));
        assertEquals(Map.of("disk", "scsi2", "size", 10, "storage", "fast-ssd"), result.getData());
        assertEquals(Map.of("size", 5, "storage", "local-lvm"), vm.getDataDisk().get("scsi1"));
        assertEquals(Map.of("size", 10, "storage", "fast-ssd"), vm.getDataDisk().get("scsi2"));
        verify(service).updateById(vm);
    }

    @Test
    void omittedStorageUsesVmStorageWithAllocationSyntax() {
        UnifiedResultDto<Object> result = service.addVmDataDisk(HOST_ID, 10, null);

        assertEquals(UnifiedResultCode.SUCCESS, result.getResultCode());
        assertEquals(Map.of("scsi2", "local-lvm:10,discard=on"), writes.get(0));
        assertEquals(Map.of("size", 10, "storage", "local-lvm"), vm.getDataDisk().get("scsi2"));
    }

    @Test
    void rejectedPveAllocationDoesNotChangeMetadata() {
        writeStatus = 400;
        Map<Object, Object> originalDisks = vm.getDataDisk();

        UnifiedResultDto<Object> result = service.addVmDataDisk(HOST_ID, 10, "fast-ssd");

        assertEquals(UnifiedResultCode.ERROR_UNKNOWN, result.getResultCode());
        assertTrue(result.getMessage().contains("新增数据盘失败"));
        assertEquals(1, writes.size());
        assertSame(originalDisks, vm.getDataDisk());
        assertEquals(Map.of("scsi1", Map.of("size", 5, "storage", "local-lvm")), vm.getDataDisk());
        verify(service, never()).updateById(any(Vmhost.class));
    }
}
