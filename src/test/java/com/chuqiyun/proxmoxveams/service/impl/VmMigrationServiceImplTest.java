package com.chuqiyun.proxmoxveams.service.impl;

import com.chuqiyun.proxmoxveams.constant.TaskType;
import com.chuqiyun.proxmoxveams.dto.VmMigrationBatchDto;
import com.chuqiyun.proxmoxveams.dto.VmMigrationLogView;
import com.chuqiyun.proxmoxveams.dto.VmMigrationParams;
import com.chuqiyun.proxmoxveams.dto.VmMigrationTaskView;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VmMigrationServiceImplTest {
    private TaskService taskService;
    private VmhostService vmhostService;
    private MasterService masterService;
    private VmMigrationServiceImpl service;
    private final List<Task> inserted = new ArrayList<>();

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        vmhostService = mock(VmhostService.class);
        masterService = mock(MasterService.class);
        inserted.clear();
        service = new VmMigrationServiceImpl();
        ReflectionTestUtils.setField(service, "taskService", taskService);
        ReflectionTestUtils.setField(service, "vmhostService", vmhostService);
        ReflectionTestUtils.setField(service, "masterService", masterService);
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
        when(vmhostService.getNewVmid(2)).thenReturn(201, 202, 203);
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
        }
        assertEquals(1, batchIds.stream().distinct().count());
        assertEquals(result.getBatchId(), batchIds.get(0));
    }

    @Test
    void batchSubmitRejectsSameNodeAndBusyVms() {
        when(masterService.getById(2)).thenReturn(targetNode());
        when(vmhostService.getById(1)).thenReturn(vmhost(1, 101, 2));
        when(vmhostService.getById(2)).thenReturn(vmhost(2, 102, 1));
        when(vmhostService.getNewVmid(2)).thenReturn(202);
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
        verify(taskService).updateTask(task);
    }

    @Test
    void resumeRejectsNonResumableStage() {
        Task task = new Task();
        task.setId(9);
        task.setType(TaskType.MIGRATE_VM);
        task.setStatus(3);
        task.setHostid(1);
        Map<Object, Object> params = new HashMap<>();
        params.put("lastFailedStep", "CLEANUP_SOURCE");
        task.setParams(params);
        when(taskService.getById(9)).thenReturn(task);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.resume(9));
        assertTrue(error.getMessage().contains("不支持续跑"));
        verify(taskService, never()).updateTask(any());
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
        assertEquals(1L, view.getNextOffset());
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
}
