package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.constant.TaskType;
import com.chuqiyun.proxmoxveams.dto.VmMigrationBatchDto;
import com.chuqiyun.proxmoxveams.dto.VmMigrationLogView;
import com.chuqiyun.proxmoxveams.dto.VmMigrationParams;
import com.chuqiyun.proxmoxveams.dto.VmMigrationTaskView;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.MigrationExecutionLock;
import com.chuqiyun.proxmoxveams.service.MigrationTerminationGuard;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.service.VmIdReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VmMigrationServiceImplTest {
    private TaskService taskService;
    private VmhostService vmhostService;
    private MasterService masterService;
    private VmIdReservationService reservationService;
    private MigrationExecutionLock executionLock;
    private MigrationTerminationGuard terminationGuard;
    private VmMigrationServiceImpl service;
    private final List<Task> inserted = new ArrayList<>();

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        vmhostService = mock(VmhostService.class);
        masterService = mock(MasterService.class);
        reservationService = mock(VmIdReservationService.class);
        executionLock = mock(MigrationExecutionLock.class);
        terminationGuard = mock(MigrationTerminationGuard.class);
        inserted.clear();
        service = spy(new VmMigrationServiceImpl());
        ReflectionTestUtils.setField(service, "taskService", taskService);
        ReflectionTestUtils.setField(service, "vmhostService", vmhostService);
        ReflectionTestUtils.setField(service, "masterService", masterService);
        ReflectionTestUtils.setField(service, "vmIdReservationService", reservationService);
        ReflectionTestUtils.setField(service, "migrationExecutionLock", executionLock);
        ReflectionTestUtils.setField(service, "migrationTerminationGuard", terminationGuard);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        ReflectionTestUtils.setField(service, "transactionManager", transactions);
        when(executionLock.runLocked(any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return true;
        });
        JSONObject capability = JSONObject.parseObject("{\"code\":200,\"data\":{\"protocol\":2,\"temporary_channel\":true,\"safe_restore\":true,\"source_identity\":true}}");
        doReturn(capability).when(service).requestMigrationCapabilities(any(Master.class));
        Master source = new Master();
        source.setId(1);
        source.setNodeName("source");
        when(masterService.getById(1)).thenReturn(source);
        when(reservationService.reserve(anyInt(), nullable(Integer.class), anyString(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1) == null ? 201 + inserted.size() : invocation.getArgument(1));
        when(taskService.update(any(Task.class), any())).thenReturn(true);
        when(taskService.insertTask(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(100 + inserted.size() + 1);
            inserted.add(task);
            return true;
        });
        when(vmhostService.addVmHostTask(any(), any())).thenReturn(true);
    }

    private Master targetNode() {
        Master node = new Master();
        node.setId(2);
        node.setStatus(0);
        node.setNaton(1);
        node.setNodeName("node-2");
        node.setAutoStorage("local-lvm");
        return node;
    }

    private Vmhost vmhost(int id, int vmid, int nodeId) {
        Vmhost vmhost = new Vmhost();
        vmhost.setId(id);
        vmhost.setVmid(vmid);
        vmhost.setNodeid(nodeId);
        vmhost.setHostname("vm-" + id);
        vmhost.setIfnat(1);
        return vmhost;
    }

    @Test
    void batchSubmitEnqueuesEveryHostInOneBatch() {
        when(masterService.getById(2)).thenReturn(targetNode());
        when(vmhostService.getById(1)).thenReturn(vmhost(1, 101, 1));
        when(vmhostService.getById(2)).thenReturn(vmhost(2, 102, 1));
        when(vmhostService.getById(3)).thenReturn(vmhost(3, 103, 1));
        when(taskService.getOne(any())).thenReturn(null);

        VmMigrationParams params = new VmMigrationParams();
        params.setHostIds(Arrays.asList(1, 2, 3));
        params.setTargetNodeId(2);

        VmMigrationBatchDto result = service.submit(params);

        assertEquals(3, result.getAcceptedCount());
        assertEquals(0, result.getRejectedCount());
        assertEquals(3, inserted.size());
        List<String> batchIds = new ArrayList<>();
        for (Task task : inserted) {
            assertEquals(TaskType.MIGRATE_VM, task.getType());
            assertEquals(0, task.getStatus());
            batchIds.add(String.valueOf(task.getParams().get("batchId")));
            assertEquals("PENDING", task.getParams().get("stage"));
            assertEquals(true, task.getParams().get("startAfterMigration"));
        }
        assertEquals(1, batchIds.stream().distinct().count());
        assertEquals(result.getBatchId(), batchIds.get(0));
    }

    @Test
    void batchSubmitRejectsSameNodeAndBusyVms() {
        when(masterService.getById(2)).thenReturn(targetNode());
        when(vmhostService.getById(1)).thenReturn(vmhost(1, 101, 2));
        when(vmhostService.getById(2)).thenReturn(vmhost(2, 102, 1));
        when(taskService.getOne(any())).thenReturn(new Task());

        VmMigrationParams params = new VmMigrationParams();
        params.setHostIds(Arrays.asList(1, 2));
        params.setTargetNodeId(2);

        VmMigrationBatchDto result = service.submit(params);

        assertEquals(0, result.getAcceptedCount());
        assertEquals(2, result.getRejectedCount());
        assertEquals("目标宿主机与源宿主机相同", result.getItems().get(0).getMessage());
        assertEquals("该虚拟机已有迁移任务在执行或排队", result.getItems().get(1).getMessage());
        assertTrue(inserted.isEmpty());
    }

    @Test
    void batchSubmitRejectsRepeatedAllocatedTargetVmid() {
        when(masterService.getById(2)).thenReturn(targetNode());
        when(vmhostService.getById(1)).thenReturn(vmhost(1, 101, 1));
        when(vmhostService.getById(2)).thenReturn(vmhost(2, 102, 1));
        when(reservationService.reserve(anyInt(), nullable(Integer.class), anyString(), anyInt()))
                .thenReturn(201).thenThrow(new IllegalStateException("目标VMID已被其他迁移任务预留"));
        VmMigrationParams params = new VmMigrationParams();
        params.setHostIds(Arrays.asList(1, 2));
        params.setTargetNodeId(2);

        VmMigrationBatchDto result = service.submit(params);

        assertEquals(1, result.getAcceptedCount());
        assertEquals(1, result.getRejectedCount());
        assertEquals(1, inserted.size());
        assertTrue(result.getItems().get(1).getMessage().contains("目标VMID已被其他迁移任务预留"));
    }

    @Test
    void submitRejectsTargetVmidReservedByFailedMigration() {
        when(masterService.getById(2)).thenReturn(targetNode());
        when(vmhostService.getById(1)).thenReturn(vmhost(1, 101, 1));
        Task previous = new Task();
        Map<Object, Object> previousParams = new HashMap<>();
        previousParams.put("targetNodeId", 2);
        previousParams.put("targetVmid", 201);
        previousParams.put("stage", "FAILED");
        previous.setParams(previousParams);
        when(taskService.list(any())).thenReturn(Arrays.asList(previous));
        VmMigrationParams params = new VmMigrationParams();
        params.setHostId(1);
        params.setTargetNodeId(2);
        params.setTargetVmid(201);

        VmMigrationBatchDto result = service.submit(params);

        assertEquals(0, result.getAcceptedCount());
        assertEquals(1, result.getRejectedCount());
        assertTrue(inserted.isEmpty());
    }

    @Test
    void resumeOnlyAcceptsResumableFailedStage() {
        Task task = new Task();
        task.setId(9);
        task.setType(TaskType.MIGRATE_VM);
        task.setStatus(3);
        task.setHostid(1);
        Map<Object, Object> params = new HashMap<>();
        params.put("lastFailedStep", "RESTORE");
        params.put("progress", 40);
        task.setParams(params);
        when(taskService.getById(9)).thenReturn(task);
        when(taskService.getOne(any())).thenReturn(null);

        Task resumed = service.resume(9);

        assertEquals(0, resumed.getStatus());
        assertEquals("PENDING", resumed.getParams().get("stage"));
        assertEquals("RESTORE", resumed.getParams().get("resumeFromStage"));
        assertNull(resumed.getError());
        verify(taskService).update(org.mockito.ArgumentMatchers.eq(task), any());
    }

    @Test
    void resumeRejectsNonResumableStage() {
        Task task = new Task();
        task.setId(9);
        task.setType(TaskType.MIGRATE_VM);
        task.setStatus(3);
        task.setHostid(1);
        Map<Object, Object> params = new HashMap<>();
        params.put("lastFailedStep", "UNKNOWN");
        task.setParams(params);
        when(taskService.getById(9)).thenReturn(task);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.resume(9));
        assertTrue(error.getMessage().contains("不支持续跑"));
        verify(taskService, never()).updateTask(any());
    }

    @Test
    void resumeUncommittedStartRebuildsRolledBackNetwork() {
        Task task = new Task();
        task.setId(9);
        task.setType(TaskType.MIGRATE_VM);
        task.setStatus(3);
        task.setHostid(1);
        Map<Object, Object> params = new HashMap<>();
        params.put("lastFailedStep", "START_TARGET_VM");
        task.setParams(params);
        when(taskService.getById(9)).thenReturn(task);

        Task resumed = service.resume(9);

        assertEquals("NETWORK_REBIND", resumed.getParams().get("resumeFromStage"));
    }

    @Test
    void resumeCommittedStartKeepsTargetNetwork() {
        Task task = new Task();
        task.setId(9);
        task.setType(TaskType.MIGRATE_VM);
        task.setStatus(3);
        task.setHostid(1);
        Map<Object, Object> params = new HashMap<>();
        params.put("lastFailedStep", "START_TARGET_VM");
        params.put("targetCommitted", true);
        task.setParams(params);
        when(taskService.getById(9)).thenReturn(task);

        Task resumed = service.resume(9);

        assertEquals("START_TARGET_VM", resumed.getParams().get("resumeFromStage"));
    }

    @Test
    void taskViewExposesStageAndResumableState() {
        Task task = new Task();
        task.setId(11);
        task.setType(TaskType.MIGRATE_VM);
        task.setStatus(3);
        task.setHostid(1);
        task.setError("boom");
        Map<Object, Object> params = new HashMap<>();
        params.put("sourceNodeId", 1);
        params.put("targetNodeId", 2);
        params.put("stage", "FAILED");
        params.put("lastFailedStep", "RESTORE");
        params.put("progress", 0);
        params.put("batchId", "mig-1");
        task.setParams(params);
        when(taskService.getById(11)).thenReturn(task);
        when(vmhostService.getById(1)).thenReturn(vmhost(1, 101, 1));
        when(masterService.getById(2)).thenReturn(targetNode());

        VmMigrationTaskView view = service.getTaskView(11);

        assertEquals("RESTORE", view.getStage());
        assertEquals("目标节点导入", view.getStageText());
        assertTrue(view.getResumable());
        assertEquals("mig-1", view.getBatchId());
        assertEquals("vm-1", view.getHostName());
        assertEquals("node-2", view.getTargetNodeName());
    }

    @Test
    void taskLogReturnsMainLogsWithoutCallingAgentForQueuedTask() {
        Task task = new Task();
        task.setId(12);
        task.setType(TaskType.MIGRATE_VM);
        task.setStatus(0);
        Map<Object, Object> params = new HashMap<>();
        params.put("stage", "PENDING");
        params.put("progress", 0);
        params.put("logLines", Arrays.asList("已经入队"));
        task.setParams(params);
        when(taskService.getById(12)).thenReturn(task);

        VmMigrationLogView view = service.getTaskLog(12, null);
        assertEquals(1, view.getLogs().size());
        assertTrue(view.getLogs().get(0).contains("已经入队"));
        assertEquals("v1:12:1:-1:-1", view.getNextOffset());
    }

    @Test
    void logCursorTracksMainLinesAndBothAgentByteOffsetsIndependently() {
        service = spy(service);
        Task task = new Task();
        task.setId(12);
        task.setType(TaskType.MIGRATE_VM);
        task.setStatus(1);
        Map<Object, Object> params = new HashMap<>();
        params.put("logLines", new ArrayList<>(Arrays.asList("开始迁移")));
        params.put("sourceNodeId", 1);
        params.put("targetNodeId", 2);
        task.setParams(params);
        when(taskService.getById(12)).thenReturn(task);
        Master source = new Master();
        source.setId(1);
        source.setNodeName("source");
        Master target = targetNode();
        when(masterService.getById(1)).thenReturn(source);
        when(masterService.getById(2)).thenReturn(target);
        doReturn(logResponse("源节点备份\n", 101)).when(service).requestAgentLog(source, "12", -1);
        doReturn(logResponse("目标节点恢复\n", 202)).when(service).requestAgentLog(target, "12", -1);

        VmMigrationLogView first = service.getTaskLog(12, null);
        assertEquals("v1:12:1:101:202", first.getNextOffset());
        assertEquals(Arrays.asList("开始迁移", "[source] 源节点备份", "[node-2] 目标节点恢复"), first.getLogs());

        service.appendTaskLog(task, params, "已进入下一步");
        doReturn(logResponse("源新增\n", 119)).when(service).requestAgentLog(source, "12", 101);
        doReturn(logResponse("", 202)).when(service).requestAgentLog(target, "12", 202);
        VmMigrationLogView second = service.getTaskLog(12, first.getNextOffset());
        assertEquals(Arrays.asList("已进入下一步", "[source] 源新增"), second.getLogs());
        assertEquals("v1:12:2:119:202", second.getNextOffset());
        verify(service).requestAgentLog(source, "12", 101);
        verify(service).requestAgentLog(target, "12", 202);
    }

    @Test
    void mainLogCursorRemainsIncrementalAfterRetainedWindowRollsOver() {
        Task task = new Task();
        task.setId(12);
        task.setType(TaskType.MIGRATE_VM);
        task.setStatus(0);
        Map<Object, Object> params = new HashMap<>();
        task.setParams(params);
        when(taskService.getById(12)).thenReturn(task);
        for (int i = 0; i < 500; i++) {
            service.appendTaskLog(task, params, "line-" + i);
        }
        VmMigrationLogView first = service.getTaskLog(12, null);
        service.appendTaskLog(task, params, "line-500");
        VmMigrationLogView second = service.getTaskLog(12, first.getNextOffset());
        assertEquals(Arrays.asList("line-500"), second.getLogs());
        assertEquals("v1:12:501:-1:-1", second.getNextOffset());
    }

    private JSONObject logResponse(String content, long nextOffset) {
        JSONObject data = new JSONObject();
        data.put("content", content);
        data.put("next_offset", nextOffset);
        JSONObject result = new JSONObject();
        result.put("code", 200);
        result.put("data", data);
        return result;
    }

    @Test
    void cancelOnlyWorksForQueuedTask() {
        Task task = new Task();
        task.setId(13);
        task.setType(TaskType.MIGRATE_VM);
        task.setStatus(0);
        task.setParams(new HashMap<>());
        when(taskService.getById(13)).thenReturn(task);

        assertTrue(service.cancel(13));
        assertEquals(3, task.getStatus());
        assertFalse(service.cancel(13));
        assertTrue(inserted.isEmpty());
        verify(taskService, never()).insertTask(any());
        verify(vmhostService, never()).getNewVmid(anyInt());
        verify(taskService, never()).getOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test
    void missingSafeMigrationCapabilitiesRejectsBeforeAnyResourceReservation() {
        when(masterService.getById(2)).thenReturn(targetNode());
        when(vmhostService.getById(1)).thenReturn(vmhost(1, 101, 1));
        doReturn(JSONObject.parseObject("{\"code\":200,\"data\":{\"protocol\":1}}"))
                .when(service).requestMigrationCapabilities(any(Master.class));
        VmMigrationParams params = new VmMigrationParams();
        params.setHostId(1);
        params.setTargetNodeId(2);

        VmMigrationBatchDto result = service.submit(params);

        assertEquals(0, result.getAcceptedCount());
        assertTrue(result.getItems().get(0).getMessage().contains("先升级"));
        verify(reservationService, never()).reserve(anyInt(), nullable(Integer.class), anyString(), anyInt());
        verify(taskService, never()).insertTask(any());
    }

    @Test
    void sourceCapabilityFailureAlsoRejectsBeforeResourceReservation() {
        when(masterService.getById(2)).thenReturn(targetNode());
        when(vmhostService.getById(1)).thenReturn(vmhost(1, 101, 1));
        Master source = masterService.getById(1);
        doReturn(null).when(service).requestMigrationCapabilities(source);
        VmMigrationParams params = new VmMigrationParams();
        params.setHostId(1);
        params.setTargetNodeId(2);

        assertEquals(0, service.submit(params).getAcceptedCount());
        verify(reservationService, never()).reserve(anyInt(), nullable(Integer.class), anyString(), anyInt());
        verify(taskService, never()).insertTask(any());
    }

    @Test
    void cancelCannotReleaseReservationAfterWorkerHasClaimedTask() {
        Task task = new Task();
        task.setId(13);
        task.setType(TaskType.MIGRATE_VM);
        task.setStatus(0);
        task.setParams(new HashMap<>(Map.of("vmIdReservationOwner", "migration:13")));
        when(taskService.getById(13)).thenReturn(task);
        when(taskService.update(any(Task.class), any())).thenReturn(false);

        assertFalse(service.cancel(13));
        verify(reservationService, never()).release(anyString());
    }

    @Test
    void cancellingQueuedRetryRetainsPotentiallyRestoredTargetReservation() {
        Task task = new Task();
        task.setId(13);
        task.setType(TaskType.MIGRATE_VM);
        task.setStatus(0);
        task.setParams(new HashMap<>(Map.of("vmIdReservationOwner", "migration:13", "retryCount", 1)));
        when(taskService.getById(13)).thenReturn(task);

        assertFalse(service.cancel(13));
        verify(reservationService, never()).release(anyString());
    }

    @Test
    void committedCleanupFailureCanResumeWithoutRebuildingOrReleasingTarget() {
        Task task = new Task();
        task.setId(13);
        task.setType(TaskType.MIGRATE_VM);
        task.setStatus(3);
        task.setParams(new HashMap<>(Map.of("vmIdReservationOwner", "migration:13", "lastFailedStep", "CLEANUP_SOURCE",
                "targetCommitted", true, "targetNodeId", 2, "targetVmid", 201)));
        when(taskService.getById(13)).thenReturn(task);

        Task result = service.resume(13);

        assertEquals("CLEANUP_SOURCE", result.getParams().get("resumeFromStage"));
        verify(reservationService).assertOwned(2, 201, "migration:13");
        verify(reservationService, never()).release(anyString());
    }

    private Task failedRestoreTask() {
        Task task = new Task();
        task.setId(9);
        task.setType(TaskType.MIGRATE_VM);
        task.setStatus(3);
        task.setHostid(1);
        task.setParams(new HashMap<>(Map.of("stage", "FAILED", "lastFailedStep", "RESTORE",
                "sourceNodeId", 1, "sourceVmid", 101, "targetNodeId", 2, "targetVmid", 201,
                "vmIdReservationOwner", "migration:9", "checkpointStage", "BACKUP")));
        when(taskService.getById(9)).thenReturn(task);
        return task;
    }

    @Test
    void terminateFailedRestorePreservesCheckpointsAndPreventsResume() {
        Task task = failedRestoreTask();
        task.getParams().put("sourceBackupFile", "vzdump-qemu-101-2026_10_30-15_00_00.vma.zst");

        Task terminated = service.terminate(9);

        assertEquals(3, terminated.getStatus());
        assertEquals("TERMINATED", terminated.getParams().get("stage"));
        assertEquals("RESTORE", terminated.getParams().get("terminatedFromStage"));
        assertEquals("BACKUP", terminated.getParams().get("checkpointStage"));
        assertEquals("vzdump-qemu-101-2026_10_30-15_00_00.vma.zst",
                terminated.getParams().get("sourceBackupFile"));
        assertFalse(service.isResumable(terminated, "RESTORE"));
        VmMigrationTaskView view = service.getTaskView(9);
        assertEquals("TERMINATED", view.getStage());
        assertFalse(view.getResumable());
        assertThrows(IllegalStateException.class, () -> service.resume(9));
        verify(terminationGuard).verify(org.mockito.ArgumentMatchers.eq(task), any());
        verify(reservationService).assertOwned(2, 201, "migration:9");
        verify(reservationService).release("migration:9");
        verify(vmhostService, never()).updateById(any(Vmhost.class));
    }

    @Test
    void terminationIsIdempotentAndDoesNotReleaseAnotherReservation() {
        Task task = failedRestoreTask();

        assertEquals(task, service.terminate(9));
        assertEquals(task, service.terminate(9));

        verify(terminationGuard, times(1)).verify(org.mockito.ArgumentMatchers.eq(task), any());
        verify(taskService, times(1)).update(any(Task.class), any());
        verify(reservationService, times(1)).release("migration:9");
    }

    @Test
    void terminationGuardFailureLeavesTaskAndReservationUntouched() {
        Task task = failedRestoreTask();
        doThrow(new IllegalStateException("目标VMID仍存在"))
                .when(terminationGuard).verify(org.mockito.ArgumentMatchers.eq(task), any());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.terminate(9));

        assertTrue(error.getMessage().contains("目标VMID"));
        assertEquals("FAILED", task.getParams().get("stage"));
        verify(taskService, never()).update(any(Task.class), any());
        verify(reservationService, never()).release(anyString());
    }

    @Test
    void terminationCasFailureNeverReleasesReservation() {
        failedRestoreTask();
        when(taskService.update(any(Task.class), any())).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.terminate(9));

        verify(reservationService, never()).release(anyString());
    }

    @Test
    void losingExecutionLockDuringRemoteChecksPreventsTaskAndReservationChanges() {
        failedRestoreTask();
        doThrow(new IllegalStateException("迁移执行锁已丢失")).when(executionLock).assertHeld();

        assertThrows(IllegalStateException.class, () -> service.terminate(9));

        verify(taskService, never()).update(any(Task.class), any());
        verify(reservationService, never()).release(anyString());
    }

    @Test
    void terminationCannotProceedWhenAnotherWorkerHoldsExecutionLock() {
        doReturn(false).when(executionLock).runLocked(any());

        assertThrows(IllegalStateException.class, () -> service.terminate(9));

        verify(taskService, never()).getById(anyInt());
        verify(taskService, never()).update(any(Task.class), any());
        verify(reservationService, never()).release(anyString());
    }

    @Test
    void resumeCannotQueueTaskWhenAnotherWorkerHoldsExecutionLock() {
        doReturn(false).when(executionLock).runLocked(any());

        assertThrows(IllegalStateException.class, () -> service.resume(9));

        verify(taskService, never()).getById(anyInt());
        verify(taskService, never()).update(any(Task.class), any());
    }

    @Test
    void terminationRejectsQueuedRunningSuccessfulAndCancelledTasks() {
        Task task = failedRestoreTask();
        for (int status : new int[]{0, 1, 2}) {
            task.setStatus(status);
            assertThrows(IllegalStateException.class, () -> service.terminate(9));
        }
        task.setStatus(3);
        task.getParams().put("stage", "CANCELLED");
        assertThrows(IllegalStateException.class, () -> service.terminate(9));

        verify(terminationGuard, never()).verify(any(Task.class), any());
        verify(reservationService, never()).release(anyString());
    }

    @Test
    void terminatedTaskNoLongerBlocksNewMigrationToTheSameTargetVmid() {
        Task task = failedRestoreTask();
        when(masterService.getById(2)).thenReturn(targetNode());
        when(vmhostService.getById(1)).thenReturn(vmhost(1, 101, 1));
        when(taskService.list(any())).thenReturn(Arrays.asList(task));
        VmMigrationParams request = new VmMigrationParams();
        request.setHostId(1);
        request.setTargetNodeId(2);
        request.setTargetVmid(201);

        service.terminate(9);
        VmMigrationBatchDto result = service.submit(request);

        assertEquals(1, result.getAcceptedCount());
        assertEquals(0, result.getRejectedCount());
        assertEquals(201, inserted.get(0).getParams().get("targetVmid"));
        verify(reservationService).reserve(org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(201), anyString(), org.mockito.ArgumentMatchers.eq(1));
    }

    @Test
    void terminationCanRecordUnrevokedChannelWhenSourceNodeHasBeenRemoved() {
        Task task = failedRestoreTask();
        task.getParams().put("sourceNodeId", 999);
        task.getParams().put("sourceTransferChannel", "old-task-ticket");

        Task terminated = service.terminate(9);

        assertEquals("TERMINATED", terminated.getParams().get("stage"));
        assertEquals("old-task-ticket", terminated.getParams().get("sourceTransferChannel"));
        assertTrue(String.valueOf(terminated.getParams().get("logLines")).contains("等待票据到期"));
        verify(reservationService).release("migration:9");
    }
}
