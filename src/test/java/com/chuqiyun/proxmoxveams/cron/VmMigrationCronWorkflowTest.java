package com.chuqiyun.proxmoxveams.cron;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.entity.Ippool;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import com.chuqiyun.proxmoxveams.service.IppoolService;
import com.chuqiyun.proxmoxveams.service.IpstatusService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.MigrationExecutionLock;
import com.chuqiyun.proxmoxveams.service.NatForwardSyncService;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmIdReservationService;
import com.chuqiyun.proxmoxveams.service.VmMigrationService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.service.VncinfoService;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 使用模拟 PVE 与被控执行完整阶段链；不会连接实际节点或运行宿主机命令。 */
class VmMigrationCronWorkflowTest {
    private static final String TOKEN = "manager-test-token";
    private static final String TRANSFER_TOKEN = "temporary-channel-secret";
    private static final String CHANNEL = "channel_abcdefghijklmnop";
    private static final String FILENAME = "vzdump-qemu-101-2026_10_30-16_00_00.vma.zst";
    private static final String SHA256 = "a".repeat(64);
    private TestMigrationCron cron;
    private Task task;
    private Vmhost vm;
    private Master source;
    private Master target;
    private TaskService tasks;
    private VmhostService hosts;
    private IppoolService ips;
    private VmIdReservationService reservations;
    private ProxmoxApiUtil pve;
    private final List<String> operations = new ArrayList<>();
    private final List<String> savedParams = new ArrayList<>();
    private final AtomicBoolean targetRunning = new AtomicBoolean();

    @BeforeEach
    void setUp() {
        operations.clear();
        savedParams.clear();
        targetRunning.set(false);
        cron = new TestMigrationCron();
        tasks = mock(TaskService.class);
        hosts = mock(VmhostService.class);
        ips = mock(IppoolService.class);
        reservations = mock(VmIdReservationService.class);
        pve = mock(ProxmoxApiUtil.class);
        ConfigService config = mock(ConfigService.class);
        MasterService masters = mock(MasterService.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        inject("taskService", tasks);
        inject("vmMigrationService", mock(VmMigrationService.class));
        inject("vmhostService", hosts);
        inject("ippoolService", ips);
        inject("ipstatusService", mock(IpstatusService.class));
        inject("natForwardSyncService", mock(NatForwardSyncService.class));
        inject("vncinfoService", mock(VncinfoService.class));
        inject("masterService", masters);
        inject("configService", config);
        inject("vmIdReservationService", reservations);
        inject("transactionManager", transactions);
        inject("migrationExecutionLock", mock(MigrationExecutionLock.class));
        source = node(1, "source", "192.0.2.1");
        source.setNodePublicKey("source-pinned-certificate");
        target = node(2, "target", "192.0.2.2");
        when(masters.getById(1)).thenReturn(source);
        when(masters.getById(2)).thenReturn(target);
        when(masters.getMasterCookieMap(any())).thenReturn(new HashMap<>());
        when(config.getToken()).thenReturn(TOKEN);
        vm = new Vmhost();
        vm.setId(17);
        vm.setNodeid(1);
        vm.setVmid(101);
        vm.setStatus(0);
        vm.setIfnat(0);
        vm.setNetworkType("classic");
        vm.setOsType("linux");
        vm.setStorage("local-lvm");
        vm.setBridge("vmbr0");
        vm.setNet0("virtio=02:00:00:00:00:17,bridge=vmbr0");
        vm.setBandwidth(100);
        vm.setIpConfig(Map.of("1", "ip=198.51.100.17/24,gw=198.51.100.1"));
        vm.setIpList(List.of("198.51.100.17"));
        when(hosts.getById(17)).thenReturn(vm);
        when(hosts.updateById(any(Vmhost.class))).thenReturn(true);
        when(hosts.update(any(Vmhost.class), any())).thenReturn(true);
        Ippool free = new Ippool();
        free.setId(51);
        free.setNodeId(2);
        free.setIp("198.51.100.51");
        free.setIpVersion(4);
        free.setSubnetMask("24");
        free.setGateway("198.51.100.1");
        free.setDns1("223.5.5.5");
        free.setPoolId(3);
        free.setStatus(0);
        free.setVmId(0);
        when(ips.getOne(any())).thenReturn(free);
        when(ips.bindFreeIppool(51, 201)).thenReturn(true);
        when(ips.releaseBoundIppool(51, 201)).thenReturn(true);
        when(ips.releaseIppoolByNodeIdAndVmId(1, 101, List.of("198.51.100.17")))
                .thenAnswer(invocation -> {
                    operations.add("release-source-ip");
                    return 1;
                });
        when(tasks.updateTask(any(Task.class))).thenAnswer(invocation -> {
            Task saved = invocation.getArgument(0);
            savedParams.add(JSON.toJSONString(saved.getParams()));
            return true;
        });
        doAnswer(invocation -> {
            operations.add("release-vmid");
            return null;
        }).when(reservations).release("migration:41");
        when(pve.getVmConfig(eq(target), any(), eq(201)))
                .thenReturn(JSONObject.parseObject(JSON.toJSONString(Map.of("net0", vm.getNet0()))));
        when(pve.getVmStatus(eq(target), any(), eq(201)))
                .thenAnswer(invocation -> data(Map.of("status", targetRunning.get() ? "running" : "stopped")));
        when(pve.postNodeApi(eq(target), any(), eq("/nodes/target/qemu/201/status/start"), any()))
                .thenAnswer(invocation -> {
                    targetRunning.set(true);
                    operations.add("start-target");
                    return data("UPID:target-start");
                });
        task = new Task();
        task.setId(41);
        task.setHostid(17);
        task.setNodeid(1);
        task.setVmid(101);
        task.setStatus(1);
        Map<Object, Object> params = new LinkedHashMap<>();
        params.put("sourceNodeId", 1);
        params.put("targetNodeId", 2);
        params.put("sourceVmid", 101);
        params.put("targetVmid", 201);
        params.put("targetStorage", "local-lvm");
        params.put("vmIdReservationOwner", "migration:41");
        params.put("startAfterMigration", true);
        task.setParams(params);
    }

    @Test
    void completesMigrationWithTemporaryDirectChannelBeforeReleasingSourceResources() {
        ReflectionTestUtils.invokeMethod(cron, "execute", task);

        assertEquals(2, task.getStatus());
        assertEquals("DONE", task.getParams().get("stage"));
        assertTrue(Boolean.TRUE.equals(task.getParams().get("targetCommitted")));
        assertEquals(2, vm.getNodeid());
        assertEquals(201, vm.getVmid());
        assertEquals(List.of("198.51.100.51"), vm.getIpList());
        assertFalse(task.getParams().containsKey("sourceTransferChannel"));
        assertEquals(List.of("backup", "channel-create", "restore-direct", "channel-renew", "channel-revoke",
                "start-target", "cleanup-source", "release-source-ip", "release-vmid"), operations);
        assertTrue(savedParams.stream().noneMatch(params -> params.contains(TRANSFER_TOKEN)));
        verify(ips, never()).releaseBoundIppool(51, 201);
        verify(reservations).release("migration:41");
    }

    @Test
    void networkFailureReclaimsOnlyTargetAddressAndPreservesSourceVmAndIp() {
        doThrow(new IllegalStateException("cloud-init failed")).when(pve)
                .resetVmCloudinit(eq(target), any(), eq(201));

        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(cron, "execute", task));

        assertEquals("NETWORK_REBIND", task.getParams().get("lastFailedStep"));
        assertEquals(1, vm.getNodeid());
        assertEquals(101, vm.getVmid());
        assertEquals(List.of("198.51.100.17"), vm.getIpList());
        assertFalse(task.getParams().containsKey("targetNetwork"));
        assertFalse(task.getParams().containsKey("sourceTransferChannel"));
        assertFalse(Boolean.TRUE.equals(task.getParams().get("targetCommitted")));
        assertFalse(targetRunning.get());
        assertEquals(List.of("backup", "channel-create", "restore-direct", "channel-renew", "channel-revoke"), operations);
        verify(ips).releaseBoundIppool(51, 201);
        verify(ips, never()).releaseIppoolByNodeIdAndVmId(1, 101, List.of("198.51.100.17"));
        verify(reservations, never()).release(any());
    }

    @Test
    void channelRevocationFailureStopsBeforeNetworkCutoverAndKeepsRetryHandle() {
        cron.revocationFails = true;

        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(cron, "execute", task));

        assertEquals(CHANNEL, task.getParams().get("sourceTransferChannel"));
        assertEquals("RESTORE", task.getParams().get("lastFailedStep"));
        assertEquals(1, vm.getNodeid());
        assertEquals(101, vm.getVmid());
        assertEquals(List.of("backup", "channel-create", "restore-direct", "channel-renew", "channel-revoke"), operations);
        verify(ips, never()).bindFreeIppool(51, 201);
        verify(ips, never()).releaseIppoolByNodeIdAndVmId(1, 101, List.of("198.51.100.17"));
        verify(reservations, never()).release(any());
    }

    @Test
    void restartRevokesPersistedChannelBeforeCreatingAnotherOne() {
        task.getParams().put("sourceTransferChannel", CHANNEL);

        ReflectionTestUtils.invokeMethod(cron, "execute", task);

        assertEquals("channel-revoke", operations.get(0));
        assertEquals(2, operations.stream().filter("channel-revoke"::equals).count());
        assertFalse(task.getParams().containsKey("sourceTransferChannel"));
        assertEquals(2, task.getStatus());
    }

    private Master node(int id, String name, String host) {
        Master node = new Master();
        node.setId(id);
        node.setNodeName(name);
        node.setHost(host);
        node.setControllerPort(7600);
        node.setAutoStorage("local-lvm");
        return node;
    }

    private void inject(String field, Object value) {
        ReflectionTestUtils.setField(cron, field, value);
    }

    private JSONObject data(Object value) {
        JSONObject response = new JSONObject();
        response.put("code", 200);
        response.put("data", value);
        return response;
    }

    private class TestMigrationCron extends VmMigrationCron {
        private boolean revocationFails;

        @Override
        protected ProxmoxApiUtil newProxmoxApi() {
            return pve;
        }

        @Override
        protected JSONObject requestMigrationCapabilities(Master node, String token) {
            return data(Map.of("protocol", 2, "temporary_channel", true, "safe_restore", true, "source_identity", true));
        }

        @Override
        protected JSONObject requestMigrationBackup(Master node, String token, String taskId, Integer vmid, String backupDir) {
            assertEquals(source, node);
            assertEquals(101, vmid);
            operations.add("backup");
            return data(Map.of("status", "RUNNING"));
        }

        @Override
        protected JSONObject requestMigrationRestore(Master node, String token, String taskId, String sourceUrl,
                                                       String sourceToken, Integer targetVmid, String storage, String backupDir,
                                                       String certificate, Long size, String sha256) {
            assertEquals(target, node);
            assertEquals("https://192.0.2.1:7600/migration-transfer/" + CHANNEL + "/" + FILENAME, sourceUrl);
            assertEquals(TRANSFER_TOKEN, sourceToken);
            assertNotEquals(TOKEN, sourceToken);
            assertEquals("source-pinned-certificate", certificate);
            assertEquals(201, targetVmid);
            assertEquals(10L, size);
            assertEquals(SHA256, sha256);
            operations.add("restore-direct");
            return data(Map.of("status", "RUNNING"));
        }

        @Override
        protected JSONObject requestMigrationStatus(Master node, String token, String taskId) {
            if (node.getId() == 1) {
                return data(Map.of("status", "SUCCESS", "stage", "BACKUP_DONE", "progress", 100,
                        "backup_file", "/home/software/QAgent/migration/backups/" + FILENAME,
                        "backup_size", 10L, "backup_sha256", SHA256));
            }
            return data(Map.of("status", "SUCCESS", "stage", "RESTORE_DONE", "progress", 100));
        }

        @Override
        protected JSONObject requestCleanupMigrationSource(Master node, String token, String taskId, Integer vmid) {
            assertEquals(source, node);
            assertEquals(101, vmid);
            assertTrue(Boolean.TRUE.equals(task.getParams().get("targetCommitted")));
            assertTrue(targetRunning.get());
            operations.add("cleanup-source");
            return data(Map.of("status", "SUCCESS"));
        }

        @Override
        protected JSONObject requestMigrationChannel(Master node, String token, String taskId, String action, String channelId) {
            assertEquals(source, node);
            assertEquals("41", taskId);
            operations.add("channel-" + action);
            if ("revoke".equals(action) && revocationFails) {
                JSONObject failure = new JSONObject();
                failure.put("code", 500);
                failure.put("message", "revoke failed");
                return failure;
            }
            if ("create".equals(action)) {
                return data(Map.of("channel_id", CHANNEL, "token", TRANSFER_TOKEN,
                        "path", "/migration-transfer/" + CHANNEL + "/" + FILENAME, "size", 10L, "sha256", SHA256));
            }
            assertEquals(CHANNEL, channelId);
            return data(Map.of("status", "SUCCESS"));
        }
    }
}
