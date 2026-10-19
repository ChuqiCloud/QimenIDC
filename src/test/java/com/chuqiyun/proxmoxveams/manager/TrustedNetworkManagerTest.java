package com.chuqiyun.proxmoxveams.manager;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.chuqiyun.proxmoxveams.service.ConfigService;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrustedNetworkManagerTest {
    private TrustedNetworkManager manager;
    private JdbcTemplate jdbc;
    private ConfigService config;

    @BeforeEach
    void setUp() throws Exception {
        manager = new TrustedNetworkManager();
        jdbc = mock(JdbcTemplate.class);
        config = mock(ConfigService.class);
        when(config.getToken()).thenReturn("shared-cluster-token");
        setField(manager, "jdbc", jdbc);
        setField(manager, "configService", config);
        setField(manager, "transactionManager", mock(PlatformTransactionManager.class));
        setField(manager, "configuredMemberId", "manager-1");
    }

    private Object field(TrustedNetworkManager manager, String name) throws Exception {
        Field field = TrustedNetworkManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(manager);
    }

    private void setField(TrustedNetworkManager manager, String name, Object value) throws Exception {
        Field field = TrustedNetworkManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }

    @SuppressWarnings("unchecked")
    private List<String> split(TrustedNetworkManager manager, String addresses) throws Exception {
        Method method = TrustedNetworkManager.class.getDeclaredMethod("splitAddresses", String.class);
        method.setAccessible(true);
        return new java.util.ArrayList<>((java.util.Set<String>) method.invoke(manager, addresses));
    }

    @SuppressWarnings("unchecked")
    private void seedMemberAddresses(TrustedNetworkManager manager, String... addresses) throws Exception {
        ((java.util.Map<String, java.util.Set<String>>) field(manager, "memberAddresses"))
                .put("manager-1", new java.util.LinkedHashSet<>(List.of(addresses)));
    }

    private JSONObject agentReport(String... probes) {
        JSONObject body = new JSONObject();
        body.put("code", 200);
        JSONObject data = new JSONObject();
        data.put("probes", new JSONArray(List.of(probes)));
        body.put("data", data);
        return body;
    }

    @Test
    void splitsAndDeduplicatesDetectedAddresses() throws Exception {
        assertEquals(List.of("10.0.0.5/32", "192.0.2.9/32"),
                split(manager, " 10.0.0.5/32,192.0.2.9/32 10.0.0.5/32 "));
        assertTrue(split(manager, "").isEmpty());
        assertTrue(split(manager, null).isEmpty());
    }

    @Test
    void clusterIdIsStableAndDoesNotLeakTheJdbcUrl() throws Exception {
        setField(manager, "datasourceUrl", "jdbc:mysql://127.0.0.1:3306/pve?user=root&password=secret");

        String clusterId = manager.getClusterId();
        assertTrue(clusterId.startsWith("db-"));
        assertEquals(15, clusterId.length());
        assertFalse(clusterId.contains("127.0.0.1"));
        assertFalse(clusterId.contains("secret"));
        assertEquals(clusterId, manager.getClusterId());
    }

    @Test
    void clusterIdFallsBackWhenDatasourceIsUnknown() throws Exception {
        when(config.getToken()).thenReturn("");
        setField(manager, "datasourceUrl", "");
        assertEquals("qimenidc", manager.getClusterId());
    }

    @Test
    void localAddressesAreCidrEntries() throws Exception {
        Method method = TrustedNetworkManager.class.getDeclaredMethod("detectLocalAddresses");
        method.setAccessible(true);
        for (String address : split(manager, (String) method.invoke(manager))) {
            assertTrue(address.contains("/"), "address must be a CIDR: " + address);
        }
    }

    @Test
    void agentReportedAddressesJoinTheWhitelist() throws Exception {
        when(jdbc.queryForList(anyString(), eq(String.class), anyString(), anyLong()))
                .thenReturn(List.of("198.51.100.7/32"));
        setField(manager, "localAddresses", "10.0.0.5/32");
        seedMemberAddresses(manager, "10.0.0.5/32");

        manager.observeAgentReport(7, agentReport("10.0.0.5/32", "198.51.100.7/32"));

        List<String> networks = manager.collectNetworks();
        assertTrue(networks.contains("198.51.100.7/32"));
        assertTrue(networks.contains("10.0.0.5/32"));
        assertTrue(((java.util.Map<?, ?>) field(manager, "syncedAt")).isEmpty());
    }

    @Test
    void failedSyncDoesNotMarkNodeAsSynced() throws Exception {
        setField(manager, "localAddresses", "10.0.0.5/32");
        seedMemberAddresses(manager, "10.0.0.5/32");
        JSONObject body = agentReport("198.51.100.7/32");
        body.put("code", 403);

        manager.observeAgentReport(9, body);

        assertTrue(((java.util.Map<?, ?>) field(manager, "syncedAt")).isEmpty());
        // 失败回传里的地址不进白名单，只剩主控自报的那条
        assertEquals(List.of("10.0.0.5/32"), manager.collectNetworks());
    }

    @Test
    void multiSourceAddressesAreAllCollected() throws Exception {
        setField(manager, "localAddresses", "111.230.93.5/32 159.75.12.57/32");
        seedMemberAddresses(manager, "111.230.93.5/32", "159.75.12.57/32");

        // 两个探测源报了两个出口，不能只留一个
        assertEquals(List.of("111.230.93.5/32", "159.75.12.57/32"), manager.collectNetworks());
    }

    @Test
    void fallbackInterfaceAddressesAreAllCidr() {

        // 探测不到公网时退化为网卡地址，下发的每一条都必须带掩码
        for (String address : manager.detectInterfaceAddresses()) {
            assertTrue(address.endsWith("/32"), "address must be a host CIDR: " + address);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void describeExposesProbeOutcome() throws Exception {
        setField(manager, "localAddresses", "198.51.100.7/32");
        seedMemberAddresses(manager, "198.51.100.7/32");
        ((java.util.Map<String, String>) field(manager, "probedAddresses")).put("ip.sb", "198.51.100.7");
        ((java.util.Map<String, String>) field(manager, "probeErrors")).put("ipify", "Connection refused");

        java.util.Map<?, ?> description = manager.describe();
        // 多源探测的原始结果要能看到，排查「为什么白名单里有这个地址」时全靠它
        assertEquals("{ip.sb=198.51.100.7}", description.get("probedSources").toString());
        assertEquals("{ipify=Connection refused}", description.get("probeErrors").toString());
        assertTrue(description.get("networks").toString().contains("198.51.100.7/32"));
    }

    @Test
    void describeExposesClusterAndAddresses() throws Exception {
        setField(manager, "datasourceUrl", "jdbc:mysql://db:3306/pve");
        setField(manager, "localAddresses", "10.0.0.5/32");
        seedMemberAddresses(manager, "10.0.0.5/32");

        String description = manager.describe().toString();
        assertTrue(description.contains("clusterId=" + manager.getClusterId()));
        assertTrue(description.contains("10.0.0.5/32"));
    }
}
