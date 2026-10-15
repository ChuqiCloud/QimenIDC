package com.chuqiyun.proxmoxveams.cron;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static com.chuqiyun.proxmoxveams.constant.TaskType.QOS_PAUSE;
import static com.chuqiyun.proxmoxveams.constant.TaskType.UNPAUSE_VM;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VmStatusCronFlowTaskTest {
    private MasterService masterService;
    private TaskService taskService;
    private VmhostService vmhostService;
    private TestVmStatusCron cron;

    @BeforeEach
    void setUp() {
        masterService = mock(MasterService.class);
        taskService = mock(TaskService.class);
        vmhostService = mock(VmhostService.class);
        cron = new TestVmStatusCron();
        ReflectionTestUtils.setField(cron, "masterService", masterService);
        ReflectionTestUtils.setField(cron, "taskService", taskService);
        ReflectionTestUtils.setField(cron, "vmhostService", vmhostService);
    }

    @Test
    void queuedQosPauseUpdatesOnlyStatusAfterRecheckingQuota() {
        Task task = task(QOS_PAUSE);
        Vmhost vmhost = vmhost(20L * GIB, 10L * GIB, 8L * GIB, 0);
        when(taskService.getOne(any(QueryWrapper.class))).thenReturn(task);
        when(masterService.getById(3)).thenReturn(new Master());
        when(masterService.getVmStatusCode(3, 101)).thenReturn(0);
        when(vmhostService.getById(17)).thenReturn(vmhost);
        when(vmhostService.updateById(any(Vmhost.class))).thenReturn(true);

        cron.qosPauseVm();

        assertEquals("stop", cron.submittedAction);
        ArgumentCaptor<Vmhost> write = ArgumentCaptor.forClass(Vmhost.class);
        verify(vmhostService).updateById(write.capture());
        assertEquals(15, write.getValue().getStatus());
        assertNull(write.getValue().getExtraFlowLimit());
        assertNull(write.getValue().getFlowLimit());
        assertNull(write.getValue().getUsedFlow());
        assertEquals(2, task.getStatus());
    }

    @Test
    void failedUnpauseRestoresFlowLimitedStateForRetry() {
        Task task = task(UNPAUSE_VM);
        Map<Object, Object> params = new HashMap<>();
        params.put("previousStatus", 15);
        params.put("previousPauseInfo", "流量超限");
        task.setParams(params);
        Vmhost vmhost = vmhost(0, 10L * GIB, 0, 3);
        when(taskService.getOne(any(QueryWrapper.class))).thenReturn(task);
        when(masterService.getById(3)).thenReturn(new Master());
        when(masterService.getVmStatusCode(3, 101)).thenReturn(1);
        when(vmhostService.getById(17)).thenReturn(vmhost);
        cron.commandFailure = new IllegalStateException("PVE unavailable");

        cron.unpauseVm();

        ArgumentCaptor<Wrapper> writes = ArgumentCaptor.forClass(Wrapper.class);
        verify(vmhostService).update(writes.capture());
        UpdateWrapper<?> restore = (UpdateWrapper<?>) writes.getValue();
        assertFalse(restore.getSqlSet().contains("extra_flow_limit"));
        assertEquals(2, restore.getParamNameValuePairs().size());
        assertTrue(restore.getParamNameValuePairs().containsValue(15));
        assertTrue(restore.getParamNameValuePairs().containsValue("流量超限"));
        verify(vmhostService, never()).updateById(any(Vmhost.class));
        assertEquals(3, task.getStatus());
    }

    private Task task(Integer type) {
        Task task = new Task();
        task.setId(9);
        task.setType(type);
        task.setStatus(0);
        task.setNodeid(3);
        task.setVmid(101);
        task.setHostid(17);
        return task;
    }

    private Vmhost vmhost(double used, long limit, long extra, int status) {
        Vmhost vmhost = new Vmhost();
        vmhost.setId(17);
        vmhost.setNodeid(3);
        vmhost.setVmid(101);
        vmhost.setUsedFlow(used);
        vmhost.setFlowLimit(limit);
        vmhost.setExtraFlowLimit(extra);
        vmhost.setStatus(status);
        return vmhost;
    }

    private static final long GIB = 1024L * 1024 * 1024;

    private static class TestVmStatusCron extends VmStatusCron {
        private String submittedAction;
        private RuntimeException commandFailure;

        @Override
        protected void submitFlowPowerCommand(Master node, Task task, String action) {
            if (commandFailure != null) {
                throw commandFailure;
            }
            submittedAction = action;
        }
    }
}
