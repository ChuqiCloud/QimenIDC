package com.chuqiyun.proxmoxveams.manager;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustedNetworkManagerTest {

    private Object field(TrustedNetworkManager manager, String name) throws Exception {
        Field field = TrustedNetworkManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(manager);
    }

    private void setField(TrustedNetworkManager manager, String name, String value) throws Exception {
        Field field = TrustedNetworkManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }

    @SuppressWarnings("unchecked")
    private List<String> split(TrustedNetworkManager manager, String addresses) throws Exception {
        Method method = TrustedNetworkManager.class.getDeclaredMethod("splitAddresses", String.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(manager, addresses);
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
        TrustedNetworkManager manager = new TrustedNetworkManager();
        assertEquals(List.of("10.0.0.5/32", "192.0.2.9/32"),
                split(manager, " 10.0.0.5/32,192.0.2.9/32 10.0.0.5/32 "));
        assertTrue(split(manager, "").isEmpty());
        assertTrue(split(manager, null).isEmpty());
    }

    @Test
    void clusterIdIsStableAndDoesNotLeakTheJdbcUrl() throws Exception {
        TrustedNetworkManager manager = new TrustedNetworkManager();
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
        TrustedNetworkManager manager = new TrustedNetworkManager();
        setField(manager, "datasourceUrl", "");
        assertEquals("qimenidc", manager.getClusterId());
    }

    @Test
    void localAddressesAreCidrEntries() throws Exception {
        TrustedNetworkManager manager = new TrustedNetworkManager();
        Method method = TrustedNetworkManager.class.getDeclaredMethod("detectLocalAddresses");
        method.setAccessible(true);
        for (String address : split(manager, (String) method.invoke(manager))) {
            assertTrue(address.contains("/"), "address must be a CIDR: " + address);
        }
    }

    @Test
    void agentReportedAddressesJoinTheWhitelist() throws Exception {
        TrustedNetworkManager manager = new TrustedNetworkManager();
        setField(manager, "localAddresses", "10.0.0.5/32");

        manager.observeAgentReport(7, agentReport("10.0.0.5/32", "198.51.100.7/32"));

        List<String> networks = manager.collectNetworks();
        assertTrue(networks.contains("198.51.100.7/32"));
        assertTrue(networks.contains("10.0.0.5/32"));
        assertEquals("[7]", field(manager, "syncedAt").toString().replaceAll("=\\d+", ""));
    }

    @Test
    void failedSyncDoesNotMarkNodeAsSynced() throws Exception {
        TrustedNetworkManager manager = new TrustedNetworkManager();
        JSONObject body = agentReport("198.51.100.7/32");
        body.put("code", 403);

        manager.observeAgentReport(9, body);

        assertTrue(((java.util.Map<?, ?>) field(manager, "syncedAt")).isEmpty());
        assertTrue(manager.collectNetworks().isEmpty());
    }

    @Test
    void describeExposesClusterAndAddresses() throws Exception {
        TrustedNetworkManager manager = new TrustedNetworkManager();
        setField(manager, "datasourceUrl", "jdbc:mysql://db:3306/pve");
        setField(manager, "localAddresses", "10.0.0.5/32");

        String description = manager.describe().toString();
        assertTrue(description.contains("clusterId=" + manager.getClusterId()));
        assertTrue(description.contains("10.0.0.5/32"));
    }
}
