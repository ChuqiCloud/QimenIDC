package com.chuqiyun.proxmoxveams.cron;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.entity.VpcIpBinding;
import com.chuqiyun.proxmoxveams.service.IppoolService;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.MigrationExecutionLock;
import com.chuqiyun.proxmoxveams.service.NatForwardSyncService;
import com.chuqiyun.proxmoxveams.service.SubnetpoolService;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmMigrationService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.service.VpcIpBindingService;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VmMigrationCronRecoveryTest {
    private TestVmMigrationCron cron;
    private TaskService taskService;
    private VmhostService vmhostService;
    private IppoolService ippoolService;
    private SubnetpoolService subnetpoolService;
    private NatForwardSyncService natForwardSyncService;
    private VpcIpBindingService vpcIpBindingService;
    private MasterService masterService;
    private MigrationExecutionLock migrationExecutionLock;
    private ProxmoxApiUtil pve;

    @BeforeEach
    void setUp() {
        cron = new TestVmMigrationCron();
        taskService = mock(TaskService.class);
        vmhostService = mock(VmhostService.class);
        ippoolService = mock(IppoolService.class);
        subnetpoolService = mock(SubnetpoolService.class);
        natForwardSyncService = mock(NatForwardSyncService.class);
        vpcIpBindingService = mock(VpcIpBindingService.class);
        masterService = mock(MasterService.class);
        migrationExecutionLock = mock(MigrationExecutionLock.class);
        ConfigService configService = mock(ConfigService.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        pve = mock(ProxmoxApiUtil.class);
        cron.api = pve;
        ReflectionTestUtils.setField(cron, "taskService", taskService);
        ReflectionTestUtils.setField(cron, "vmhostService", vmhostService);
        ReflectionTestUtils.setField(cron, "ippoolService", ippoolService);
        ReflectionTestUtils.setField(cron, "subnetpoolService", subnetpoolService);
        ReflectionTestUtils.setField(cron, "natForwardSyncService", natForwardSyncService);
        ReflectionTestUtils.setField(cron, "vpcIpBindingService", vpcIpBindingService);
        ReflectionTestUtils.setField(cron, "masterService", masterService);
        ReflectionTestUtils.setField(cron, "configService", configService);
        ReflectionTestUtils.setField(cron, "transactionManager", transactions);
        ReflectionTestUtils.setField(cron, "migrationExecutionLock", migrationExecutionLock);
        ReflectionTestUtils.setField(cron, "vmMigrationService", mock(VmMigrationService.class));
        when(taskService.updateTask(any(Task.class))).thenReturn(true);
        when(vmhostService.updateById(any(Vmhost.class))).thenReturn(true);
        when(vmhostService.update(any(Vmhost.class), any())).thenReturn(true);
        Master target = new Master();
        target.setId(2);
        target.setHost("192.0.2.2");
        target.setControllerPort(7600);
        when(masterService.getById(2)).thenReturn(target);
        when(configService.getToken()).thenReturn("test-token");
        when(pve.getVmStatus(eq(target), any(), eq(201))).thenReturn(powerState("stopped"));
    }

    @Test
    void sourceCleanupFailureDoesNotRestoreSourceAfterTargetCommit() {
        Task task = task();
        task.getParams().put("targetCommitted", true);

        ReflectionTestUtils.invokeMethod(cron, "handleStageFailure", task, task.getParams(),
                "CLEANUP_SOURCE", new IllegalStateException("source unavailable"));

        assertEquals("CLEANUP_SOURCE", task.getParams().get("lastFailedStep"));
        verifyNoInteractions(vmhostService, ippoolService, subnetpoolService,
                natForwardSyncService, vpcIpBindingService);
    }

    @Test
    void backupFailurePreservesAllSourceIpAllocations() {
        Task task = task();
        Vmhost source = sourceVmhost();
        when(vmhostService.getById(17)).thenReturn(source);
        when(vpcIpBindingService.list(any())).thenReturn(List.of());

        ReflectionTestUtils.invokeMethod(cron, "handleStageFailure", task, task.getParams(),
                "BACKUP", new IllegalStateException("backup disk full"));

        assertEquals(1, source.getNodeid());
        assertEquals(101, source.getVmid());
        assertEquals(List.of("10.0.0.17"), source.getIpList());
        verifyNoInteractions(ippoolService, subnetpoolService, natForwardSyncService);
        verify(vmhostService, never()).delVmhostVpcIpForward(17, "198.51.100.17", "10.0.0.17");
    }

    @Test
    void retryFromNetworkStageDoesNotRepeatCompletedRestore() throws Exception {
        Task task = task();
        AtomicBoolean actionCalled = new AtomicBoolean();
        Class<?> actionType = Class.forName(VmMigrationCron.class.getName() + "$StageAction");
        Object action = Proxy.newProxyInstance(actionType.getClassLoader(), new Class<?>[]{actionType},
                (proxy, method, args) -> {
                    actionCalled.set(true);
                    return null;
                });
        Method stage = VmMigrationCron.class.getDeclaredMethod("migrateStage", Task.class, Map.class,
                int.class, String.class, int.class, String.class, actionType);
        stage.setAccessible(true);

        Object result = stage.invoke(cron, task, task.getParams(), 3, "RESTORE", 35,
                "目标节点开始导入", action);

        assertNull(result);
        assertFalse(actionCalled.get());
        verifyNoInteractions(vmhostService, ippoolService, subnetpoolService, natForwardSyncService);
    }

    @Test
    void keepsSourceVpcIpsAndRulesWhenAgentDeletionFails() throws Exception {
        cron.sourceDeleteSucceeds = false;

        assertThrows(IllegalStateException.class, () -> releaseSourceVpc());

        verifyNoInteractions(ippoolService, subnetpoolService, natForwardSyncService);
    }

    @Test
    void keepsSourceVpcIpsWhenLocalRuleDeletionFails() throws Exception {
        cron.sourceDeleteSucceeds = true;
        when(natForwardSyncService.deleteIpForwardRule(1, "198.51.100.17")).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> releaseSourceVpc());

        verifyNoInteractions(ippoolService, subnetpoolService);
    }

    @Test
    void releasesSourcePublicIpOnlyAfterBothRuleDeletionsSucceed() throws Exception {
        cron.sourceDeleteSucceeds = true;
        when(natForwardSyncService.deleteIpForwardRule(1, "198.51.100.17")).thenAnswer(invocation -> {
            cron.operations.add("local-delete");
            return true;
        });
        when(ippoolService.releaseIppoolByNodeIdAndVmId(1, 101, List.of("198.51.100.17")))
                .thenAnswer(invocation -> {
                    cron.operations.add("public-ip-release");
                    return 1;
                });

        releaseSourceVpc();

        assertEquals(List.of("remote-delete", "local-delete", "public-ip-release"), cron.operations);
    }

    @Test
    void recoversSavedPartialAllocationAfterManagerRestartWithoutReleasingSourceIp() {
        Task task = task();
        Map<String, Object> journal = new LinkedHashMap<>();
        journal.put("vpc", false);
        journal.put("dbSwitched", false);
        journal.put("targetNodeId", 2);
        journal.put("targetVmid", 201);
        journal.put("targetIppools", List.of(Map.of("id", 51, "nodeId", 2, "vmId", 201,
                "status", 1, "ip", "198.51.100.51")));
        // 使用落库后重新反序列化的数据，避免只验证同一进程里的对象引用。
        task.getParams().put("targetNetwork", JSON.parseObject(JSON.toJSONString(journal)));
        task.getParams().put("sourceVpcBindings", List.of());
        Vmhost source = sourceVmhost();
        when(vmhostService.getById(17)).thenReturn(source);
        when(ippoolService.releaseBoundIppool(51, 201)).thenReturn(true);

        ReflectionTestUtils.invokeMethod(cron, "handleStageFailure", task, task.getParams(),
                "NETWORK_REBIND", new IllegalStateException("manager restarted during allocation"));

        verify(ippoolService).releaseBoundIppool(51, 201);
        verify(ippoolService, never()).releaseIppoolByNodeIdAndVmId(1, 101, List.of("10.0.0.17"));
        assertFalse(task.getParams().containsKey("targetNetwork"));
        assertTrue(Boolean.TRUE.equals(task.getParams().get("networkRolledBack")));
        assertEquals(1, source.getNodeid());
        assertEquals(101, source.getVmid());
    }

    @Test
    void retainsJournalAndAllAddressesWhenTargetForwardDeletionFails() {
        Task task = taskWithVpcJournal();
        Vmhost target = sourceVmhost();
        target.setNodeid(2);
        target.setVmid(201);
        when(vmhostService.getById(17)).thenReturn(target);
        cron.sourceDeleteSucceeds = false;

        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(cron,
                "handleStageFailure", task, task.getParams(), "NETWORK_REBIND", new IllegalStateException("failed")));

        assertTrue(task.getParams().containsKey("targetNetwork"));
        assertEquals(2, target.getNodeid());
        assertEquals(201, target.getVmid());
        verifyNoInteractions(ippoolService, subnetpoolService);
        verify(vmhostService, never()).updateById(any(Vmhost.class));
    }

    @Test
    void refusesRollbackOfRunningTargetEvenIfCommitMarkerWasNotSaved() {
        Task task = taskWithVpcJournal();
        Vmhost target = sourceVmhost();
        target.setNodeid(2);
        target.setVmid(201);
        when(vmhostService.getById(17)).thenReturn(target);
        when(pve.getVmStatus(eq(masterService.getById(2)), any(), eq(201))).thenReturn(powerState("running"));

        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(cron,
                "handleStageFailure", task, task.getParams(), "NETWORK_REBIND", new IllegalStateException("failed")));

        assertTrue(task.getParams().containsKey("targetNetwork"));
        assertEquals(2, target.getNodeid());
        verifyNoInteractions(ippoolService, subnetpoolService, natForwardSyncService);
        assertTrue(cron.operations.isEmpty());
    }

    @Test
    void doesNotExecuteTaskWhenAnotherWorkerOrCancellationWinsClaim() {
        Task task = task();
        task.setStatus(0);
        Page<Task> page = new Page<>();
        page.setRecords(List.of(task));
        when(taskService.getTaskList(eq(1), eq(1), any())).thenReturn(page);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return true;
        }).when(migrationExecutionLock).runLocked(any(Runnable.class));

        cron.vmMigrationCron();

        assertEquals(0, task.getStatus());
        verifyNoInteractions(vmhostService, ippoolService, subnetpoolService, natForwardSyncService);
        verify(taskService, never()).updateTask(any(Task.class));
    }

    private Task taskWithVpcJournal() {
        Task task = task();
        Map<String, Object> journal = new LinkedHashMap<>();
        journal.put("vpc", true);
        journal.put("dbSwitched", true);
        journal.put("targetNodeId", 2);
        journal.put("targetVmid", 201);
        journal.put("createdVpcForwards", List.of(Map.of("publicIp", "198.51.100.51", "privateIp", "10.2.0.17")));
        journal.put("targetVpcPublicIps", List.of(Map.of("id", 51, "nodeId", 2, "vmId", 201,
                "status", 1, "ip", "198.51.100.51")));
        journal.put("targetVpcPrivateIps", List.of(Map.of("id", 61, "nodeId", 2, "vmId", 201,
                "status", 1, "ip", "10.2.0.17")));
        task.getParams().put("targetNetwork", JSON.parseObject(JSON.toJSONString(journal)));
        task.getParams().put("sourceVpcBindings", List.of());
        return task;
    }

    private JSONObject powerState(String state) {
        JSONObject response = new JSONObject();
        response.put("data", Map.of("status", state));
        return response;
    }

    private void releaseSourceVpc() throws Exception {
        Master source = new Master();
        source.setId(1);
        source.setHost("192.0.2.1");
        source.setControllerPort(7600);
        VpcIpBinding binding = new VpcIpBinding();
        binding.setId(8);
        binding.setHostId(17);
        binding.setPublicIp("198.51.100.17");
        binding.setPrivateIp("10.0.0.17");
        Class<?> snapshotType = Class.forName(VmMigrationCron.class.getName() + "$VmhostSnapshot");
        Method fromMap = snapshotType.getDeclaredMethod("fromMap", Map.class);
        fromMap.setAccessible(true);
        Object snapshot = fromMap.invoke(null, sourceSnapshot());
        ReflectionTestUtils.invokeMethod(cron, "releaseSourceNetwork", snapshot, source, List.of(),
                List.of(binding), "test-token");
    }

    private Task task() {
        Task task = new Task();
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
        params.put("sourceSnapshot", sourceSnapshot());
        params.put("logLines", new ArrayList<>());
        task.setParams(params);
        return task;
    }

    private Map<Object, Object> sourceSnapshot() {
        Map<Object, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("hostId", 17);
        snapshot.put("nodeid", 1);
        snapshot.put("vmid", 101);
        snapshot.put("storage", "local-lvm");
        snapshot.put("status", 1);
        snapshot.put("bridge", "vpc0");
        snapshot.put("networkType", "vpc");
        snapshot.put("vpcSubnetId", 8);
        snapshot.put("ifnat", 0);
        snapshot.put("vpc", true);
        snapshot.put("ipConfig", Map.of("1", "ip=10.0.0.17/24,gw=10.0.0.1"));
        snapshot.put("ipList", List.of("10.0.0.17"));
        return snapshot;
    }

    private Vmhost sourceVmhost() {
        Vmhost source = new Vmhost();
        source.setId(17);
        source.setNodeid(1);
        source.setVmid(101);
        source.setNetworkType("vpc");
        source.setVpcSubnetId(8);
        source.setIpList(List.of("10.0.0.17"));
        source.setIpConfig(Map.of("1", "ip=10.0.0.17/24,gw=10.0.0.1"));
        return source;
    }

    private static class TestVmMigrationCron extends VmMigrationCron {
        private boolean sourceDeleteSucceeds;
        private final List<String> operations = new ArrayList<>();
        private ProxmoxApiUtil api;

        @Override
        protected ProxmoxApiUtil newProxmoxApi() {
            return api;
        }

        @Override
        protected Boolean requestDeleteIpForward(Master node, String token, Integer hostId,
                                                 String publicIp, String privateIp) {
            operations.add("remote-delete");
            return sourceDeleteSucceeds;
        }
    }
}
