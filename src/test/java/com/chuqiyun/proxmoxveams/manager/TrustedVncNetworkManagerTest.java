package com.chuqiyun.proxmoxveams.manager;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Vncnode;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.VncnodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @Author: 星禾
 * @Description: VNC 多来源自动下发及变更补偿测试
 * @DateTime: 2026/11/2 21:20
 */
class TrustedVncNetworkManagerTest {
    private StubManager manager;
    private VncnodeService vncnodeService;

    @BeforeEach
    void setUp() {
        manager = new StubManager();
        vncnodeService = mock(VncnodeService.class);
        MasterService masterService = mock(MasterService.class);
        ConfigService configService = mock(ConfigService.class);
        when(configService.getToken()).thenReturn("test-token");
        Master master = new Master();
        master.setId(13);
        master.setName("node-13");
        master.setHost("192.0.2.13");
        master.setControllerPort(7600);
        when(masterService.list(any())).thenReturn(List.of(master));
        ReflectionTestUtils.setField(manager, "vncnodeService", vncnodeService);
        ReflectionTestUtils.setField(manager, "masterService", masterService);
        ReflectionTestUtils.setField(manager, "configService", configService);
        ReflectionTestUtils.setField(manager, "jdbc", mock(JdbcTemplate.class));
        ReflectionTestUtils.setField(manager, "configuredMemberId", "manager-test");
        ReflectionTestUtils.setField(manager, "localAddresses", "198.51.100.1/32");
        ReflectionTestUtils.setField(manager, "lastFullSyncAt", System.currentTimeMillis());
        members().put("manager-test", new LinkedHashSet<>(List.of("198.51.100.1/32")));
    }

    @Test
    void collectsAllEnabledHostsAndAllDnsAddressesWithoutUsingBrowserDomain() throws Exception {
        Vncnode first = node(1, "relay.example", 0);
        first.setDomain("cdn-browser.example");
        first.setProxy(0);
        manager.resolved.put("relay.example", addresses("103.109.20.138", "2001:db8::1", "103.109.20.139"));
        when(vncnodeService.list(any())).thenReturn(List.of(first,
                node(2, "103.109.20.138", 0), node(3, "disabled.example", 1), node(4, "150.138.77.26", 0)));

        List<String> networks = manager.collectVncNetworks();

        assertEquals(Set.of("103.109.20.138/32", "103.109.20.139/32", "150.138.77.26/32",
                InetAddress.getByName("2001:db8::1").getHostAddress() + "/128"), new LinkedHashSet<>(networks));
        verify(vncnodeService).list(argThat(query -> query.getSqlSegment().contains("status")));
    }

    @Test
    void explicitProxySourcesOverrideListeningHostAndNormalizeAllEgressAddresses() throws Exception {
        Vncnode relay = node(1, "unresolved-listen-address.example", 0);
        relay.setProxySourceIps("103.109.20.138, 103.109.20.139/32\n2001:db8::1234/64 103.109.20.138");
        when(vncnodeService.list(any())).thenReturn(List.of(relay, node(2, "150.138.77.26", 0)));

        assertEquals(Set.of("103.109.20.138/32", "103.109.20.139/32", "150.138.77.26/32",
                InetAddress.getByName("2001:db8::").getHostAddress() + "/64"),
                new LinkedHashSet<>(manager.collectVncNetworks()));
    }

    @Test
    void blankExplicitProxySourcesRetainHistoricalHostFallback() {
        Vncnode relay = node(1, "103.109.20.138", 0);
        relay.setProxySourceIps(" \n ");
        when(vncnodeService.list(any())).thenReturn(List.of(relay));

        assertEquals(List.of("103.109.20.138/32"), manager.collectVncNetworks());
    }

    @Test
    void cidrAcknowledgementAllowsCompressedIpv6AndHostBitsAreCanonicalized() {
        Vncnode relay = node(1, "103.109.20.138", 0);
        relay.setProxySourceIps("192.0.2.29/24 2001:db8::1234/64");
        when(vncnodeService.list(any())).thenReturn(List.of(relay));
        JSONObject data = new JSONObject();
        data.put("cluster_vnc_addresses", new JSONArray(List.of("192.0.2.0/24", "2001:db8::/64")));
        manager.response = report(data);

        manager.onVncNetworksChanged(new VncNetworksChangedEvent());

        assertTrue(synced().containsKey(13));
    }

    @Test
    void invalidExplicitProxySourcePreservesInstalledPolicyInsteadOfFallingBack() {
        Vncnode relay = node(1, "103.109.20.138", 0);
        relay.setProxySourceIps("0.0.0.0/0");
        when(vncnodeService.list(any())).thenReturn(List.of(relay));

        manager.onVncNetworksChanged(new VncNetworksChangedEvent());

        assertNull(manager.sent.get(0));
        assertTrue(pending().containsKey(13));
        assertTrue(errors().get(13).contains("出口来源配置无效"));
    }

    @Test
    void completeSnapshotRemovesDeletedSourceAndEmptySnapshotRevokesLastSource() {
        when(vncnodeService.list(any())).thenReturn(List.of(node(1, "103.109.20.138", 0),
                        node(2, "150.138.77.26", 0)),
                List.of(node(2, "150.138.77.26", 0)), List.of());

        manager.onVncNetworksChanged(new VncNetworksChangedEvent());
        manager.onVncNetworksChanged(new VncNetworksChangedEvent());
        manager.onVncNetworksChanged(new VncNetworksChangedEvent());

        assertEquals(List.of(List.of("103.109.20.138/32", "150.138.77.26/32"),
                List.of("150.138.77.26/32"), List.of()), manager.sent);
        assertTrue(pending().isEmpty());
        assertTrue(synced().containsKey(13));
    }

    @Test
    void unresolvedEnabledSourcePreservesInstalledPolicyAndLeavesSyncPending() {
        when(vncnodeService.list(any())).thenReturn(List.of(node(1, "103.109.20.138", 0)),
                List.of(node(1, "103.109.20.138", 0), node(2, "missing.example", 0)));
        manager.onVncNetworksChanged(new VncNetworksChangedEvent());

        manager.onVncNetworksChanged(new VncNetworksChangedEvent());

        assertNull(manager.sent.get(1), "查询失败必须省略 vnc_networks，不能下发不完整或空快照");
        assertFalse(synced().containsKey(13));
        assertTrue(pending().containsKey(13));
        assertTrue(errors().get(13).contains("missing.example"));
        assertEquals(List.of("103.109.20.138/32"), ReflectionTestUtils.getField(manager, "vncNetworks"));
    }

    @Test
    void rejectsInvalidAndWildcardHostsInsteadOfTurningThemIntoEmptyWhitelist() {
        for (String invalid : List.of("https://user:secret@relay.example", "relay.example/path", "relay.example?x=1",
                "relay.example:invalid", "relay.example:0", "ftp://relay.example", "0.0.0.0", "::", "")) {
            when(vncnodeService.list(any())).thenReturn(List.of(node(1, invalid, 0)));
            assertThrows(IllegalStateException.class, manager::collectVncNetworks, invalid);
        }
    }

    @Test
    void acceptsHistoricalHostUrlsAndPortsWithoutWhitelistingTheirBrowserPath() throws Exception {
        manager.resolved.put("relay.example", addresses("103.109.20.138"));
        when(vncnodeService.list(any())).thenReturn(List.of(node(1, "https://relay.example:7600/", 0),
                node(2, "103.109.20.138:7600", 0), node(3, "http://[2001:db8::1]:7600/", 0)));

        assertEquals(Set.of("103.109.20.138/32", InetAddress.getByName("2001:db8::1").getHostAddress() + "/128"),
                new LinkedHashSet<>(manager.collectVncNetworks()));
    }

    @Test
    void legacyAgentWithoutVncAcknowledgementIsNotMarkedSynced() {
        when(vncnodeService.list(any())).thenReturn(List.of(node(1, "103.109.20.138", 0)));
        manager.response = report(new JSONObject());

        manager.onVncNetworksChanged(new VncNetworksChangedEvent());

        assertTrue(synced().isEmpty());
        assertTrue(pending().containsKey(13));
        assertTrue(errors().get(13).contains("1.1.1"));
    }

    @Test
    void verifiesOwnClusterSnapshotAndAllowsOtherClustersInEffectiveWhitelist() {
        when(vncnodeService.list(any())).thenReturn(List.of(node(1, "2001:db8::1", 0)));
        JSONObject data = new JSONObject();
        data.put("cluster_vnc_addresses", new JSONArray(List.of("2001:db8::1/128")));
        data.put("vnc_addresses", new JSONArray(List.of("2001:db8::1/128", "192.0.2.100/32")));
        manager.response = report(data);

        manager.onVncNetworksChanged(new VncNetworksChangedEvent());

        assertTrue(synced().containsKey(13));
        data.put("cluster_vnc_addresses", new JSONArray(List.of("2001:db8::2/128")));
        manager.onVncNetworksChanged(new VncNetworksChangedEvent());
        assertTrue(synced().isEmpty());
        assertTrue(errors().get(13).contains("不一致"));
    }

    @Test
    void periodicSyncDetectsNewDnsAddressEvenInsideSuccessfulSyncInterval() throws Exception {
        when(vncnodeService.list(any())).thenReturn(List.of(node(1, "relay.example", 0)));
        manager.resolved.put("relay.example", addresses("103.109.20.138"));
        manager.onVncNetworksChanged(new VncNetworksChangedEvent());
        manager.resolved.put("relay.example", addresses("150.138.77.26"));

        manager.describe();
        manager.retryPending();

        assertEquals(List.of("150.138.77.26/32"), manager.sent.get(1));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> members() {
        return (Map<String, Set<String>>) ReflectionTestUtils.getField(manager, "memberAddresses");
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Long> pending() {
        return (Map<Integer, Long>) ReflectionTestUtils.getField(manager, "nextRetryAt");
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Long> synced() {
        return (Map<Integer, Long>) ReflectionTestUtils.getField(manager, "syncedAt");
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, String> errors() {
        return (Map<Integer, String>) ReflectionTestUtils.getField(manager, "syncErrors");
    }

    private Vncnode node(int id, String host, int status) {
        Vncnode node = new Vncnode();
        node.setId(id);
        node.setHost(host);
        node.setStatus(status);
        return node;
    }

    private static InetAddress[] addresses(String... literals) throws UnknownHostException {
        InetAddress[] addresses = new InetAddress[literals.length];
        for (int i = 0; i < literals.length; i++) {
            addresses[i] = InetAddress.getByName(literals[i]);
        }
        return addresses;
    }

    private static JSONObject report(JSONObject data) {
        JSONObject result = new JSONObject();
        result.put("code", 200);
        result.put("data", data);
        return result;
    }

    private static class StubManager extends TrustedNetworkManager {
        private final Map<String, InetAddress[]> resolved = new HashMap<>();
        private final List<List<String>> sent = new ArrayList<>();
        private JSONObject response;

        @Override
        protected InetAddress[] resolveVncHost(String host) throws IOException {
            if (resolved.containsKey(host)) {
                return resolved.get(host);
            }
            if (host.matches("[0-9a-fA-F:.]+")) {
                return addresses(host);
            }
            throw new UnknownHostException(host);
        }

        @Override
        protected JSONObject postTrustedNetworks(Master node, String token, List<String> networks,
                                                 List<String> vncSnapshot, String cluster) {
            sent.add(vncSnapshot == null ? null : new ArrayList<>(vncSnapshot));
            if (response != null) {
                return response;
            }
            JSONObject data = new JSONObject();
            if (vncSnapshot != null) {
                data.put("cluster_vnc_addresses", new JSONArray(vncSnapshot));
            }
            return report(data);
        }
    }
}
