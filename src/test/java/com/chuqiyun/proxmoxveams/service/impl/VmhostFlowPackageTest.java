package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dto.VmParams;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VmhostFlowPackageTest {
    private static final long GIB = 1024L * 1024 * 1024;
    private VmhostServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new VmhostServiceImpl());
        ReflectionTestUtils.setField(service, "vmIdReservationService", mock(com.chuqiyun.proxmoxveams.service.VmIdReservationService.class));
        doReturn(true).when(service).update(any(Wrapper.class));
    }

    @Test
    void absoluteQuotaRetryWritesSameTotalWithoutCreditingTwice() {
        Vmhost vm = vm(0, 12L * GIB);
        doReturn(vm).when(service).getById(17);
        VmParams params = quota(12L);

        assertEquals(UnifiedResultCode.SUCCESS, service.updateVm(params).getResultCode());
        assertEquals(UnifiedResultCode.SUCCESS, service.updateVm(params).getResultCode());

        ArgumentCaptor<Wrapper> writes = ArgumentCaptor.forClass(Wrapper.class);
        verify(service, times(2)).update(writes.capture());
        for (Wrapper<?> write : writes.getAllValues()) {
            UpdateWrapper<?> update = (UpdateWrapper<?>) write;
            assertTrue(update.getSqlSet().startsWith("extra_flow_limit="));
            assertFalse(update.getSqlSet().contains("+"));
            assertTrue(update.getParamNameValuePairs().containsValue(12L * GIB));
        }
        verify(service, never()).updateById(any(Vmhost.class));
    }

    @Test
    void failedQuotaPersistenceReturnsFailureAndDoesNotResume() {
        doReturn(vm(15, 0L)).when(service).getById(17);
        doReturn(false).when(service).update(any(Wrapper.class));

        assertEquals(UnifiedResultCode.ERROR_UNKNOWN, service.updateVm(quota(12L)).getResultCode());

        verify(service, never()).power(anyInt(), anyString(), any());
    }

    @Test
    void absoluteQuotaRestoresPausedVmWhenNewAllowanceCoversUsage() {
        Vmhost before = vm(15, 0L);
        Vmhost after = vm(15, 12L * GIB);
        after.setUsedFlow(22D * GIB);
        doReturn(before, after).when(service).getById(17);
        HashMap<String, Object> result = new HashMap<>();
        result.put("status", true);
        doReturn(result).when(service).power(17, "unpause", null);

        assertEquals(UnifiedResultCode.SUCCESS, service.updateVm(quota(12L)).getResultCode());

        verify(service).power(17, "unpause", null);
        verify(service, never()).updateById(any(Vmhost.class));
    }

    @Test
    void insufficientNewAllowanceLeavesPausedVmForFurtherPurchase() {
        Vmhost after = vm(15, GIB);
        after.setUsedFlow(22D * GIB);
        doReturn(after).when(service).getById(17);

        assertEquals(UnifiedResultCode.SUCCESS, service.updateVm(quota(1L)).getResultCode());

        verify(service, never()).power(anyInt(), anyString(), any());
    }

    @Test
    void additivePurchaseSupportsNullLegacyQuotaAndUsesAtomicIncrement() {
        doReturn(vm(0, null)).when(service).getById(17);

        assertTrue(service.addVmHostFlow(17, 3L));

        ArgumentCaptor<Wrapper> writes = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).update(writes.capture());
        UpdateWrapper<?> update = (UpdateWrapper<?>) writes.getValue();
        assertEquals("extra_flow_limit = COALESCE(extra_flow_limit, 0) + " + 3L * GIB, update.getSqlSet());
        assertTrue(update.getSqlSegment().contains("extra_flow_limit <="));
        assertTrue(update.getParamNameValuePairs().containsValue(Long.MAX_VALUE - 3L * GIB));
        verify(service, never()).updateById(any(Vmhost.class));
    }

    @Test
    void nullResumeResultDoesNotTurnCommittedQuotaIntoFailedPayment() {
        doReturn(vm(15, 12L * GIB)).when(service).getById(17);
        doReturn(null).when(service).power(17, "unpause", null);

        assertTrue(service.addVmHostFlow(17, 12L));

        ArgumentCaptor<Wrapper> writes = ArgumentCaptor.forClass(Wrapper.class);
        verify(service, times(2)).update(writes.capture());
        UpdateWrapper<?> restore = (UpdateWrapper<?>) writes.getAllValues().get(1);
        assertTrue(restore.getSqlSet().startsWith("status="));
        assertTrue(restore.getParamNameValuePairs().containsValue(15));
        assertFalse(restore.getSqlSet().contains("pause_info"));
    }

    @Test
    void bandwidthFailureKeepsFlowLimitedStateAndCommittedQuota() {
        Vmhost throttled = vm(15, 12L * GIB);
        throttled.setOutFlow(1024);
        doReturn(throttled).when(service).getById(17);
        doReturn(false).when(service).changeVmHostBandWidth(any(Vmhost.class), anyString());

        assertTrue(service.addVmHostFlow(17, 12L));

        verify(service).changeVmHostBandWidth(throttled, "12.500");
        verify(service, times(1)).update(any(Wrapper.class));
        assertEquals(15, throttled.getStatus());
    }

    @Test
    void failedResumeTaskReturnsVmToFlowLimitedStateForRetry() {
        Vmhost paused = vm(15, 12L * GIB);
        doReturn(paused).when(service).getById(17);
        TaskService taskService = mock(TaskService.class);
        ReflectionTestUtils.setField(service, "taskService", taskService);
        when(taskService.save(any(Task.class))).thenReturn(false);

        HashMap<String, Object> result = service.power(17, "unpause", null);

        assertEquals(false, result.get("status"));
        ArgumentCaptor<Task> task = ArgumentCaptor.forClass(Task.class);
        verify(taskService).save(task.capture());
        assertEquals(15, task.getValue().getParams().get("previousStatus"));
        assertEquals("流量超限", task.getValue().getParams().get("previousPauseInfo"));
        ArgumentCaptor<Wrapper> writes = ArgumentCaptor.forClass(Wrapper.class);
        verify(service, times(2)).update(writes.capture());
        UpdateWrapper<?> restore = (UpdateWrapper<?>) writes.getAllValues().get(1);
        restore.getSqlSet();
        assertTrue(restore.getParamNameValuePairs().containsValue(15));
        verify(service, never()).updateById(any(Vmhost.class));
    }

    @Test
    void failedQosPauseEnqueueRestoresStateWithoutWritingQuotaSnapshot() {
        Vmhost running = vm(0, 12L * GIB);
        doReturn(running).when(service).getById(17);
        TaskService taskService = mock(TaskService.class);
        ReflectionTestUtils.setField(service, "taskService", taskService);
        when(taskService.save(any(Task.class))).thenReturn(false);

        HashMap<String, Object> result = service.power(17, "qosPause", null);

        assertEquals(false, result.get("status"));
        ArgumentCaptor<Wrapper> writes = ArgumentCaptor.forClass(Wrapper.class);
        verify(service, times(2)).update(writes.capture());
        for (Wrapper<?> write : writes.getAllValues()) {
            assertFalse(((UpdateWrapper<?>) write).getSqlSet().contains("extra_flow_limit"));
        }
        UpdateWrapper<?> restore = (UpdateWrapper<?>) writes.getAllValues().get(1);
        restore.getSqlSet();
        assertTrue(restore.getParamNameValuePairs().containsValue(0));
        verify(service, never()).updateById(any(Vmhost.class));
    }

    @Test
    void lookupFailureAfterCommittedPurchaseDoesNotReportFailedCredit() {
        doReturn(vm(0, 0L)).doThrow(new IllegalStateException("读取失败")).when(service).getById(17);

        assertTrue(service.addVmHostFlow(17, 3L));

        verify(service).update(any(Wrapper.class));
    }

    @Test
    void invalidQuotaFailsBeforePersistence() {
        doReturn(vm(0, 0L)).when(service).getById(17);

        assertEquals(UnifiedResultCode.ERROR_INVALID_PARAM, service.updateVm(quota(-1L)).getResultCode());
        assertEquals(UnifiedResultCode.ERROR_INVALID_PARAM, service.updateVm(quota(Long.MAX_VALUE)).getResultCode());
        assertFalse(service.addVmHostFlow(17, Long.MAX_VALUE));

        verify(service, never()).update(any(Wrapper.class));
    }

    private VmParams quota(Long amount) {
        VmParams params = new VmParams();
        params.setHostid(17);
        params.setExtraFlowLimit(amount);
        return params;
    }

    private Vmhost vm(int status, Long extra) {
        Vmhost vm = new Vmhost();
        vm.setId(17);
        vm.setVmid(117);
        vm.setNodeid(3);
        vm.setStatus(status);
        vm.setFlowLimit(10L * GIB);
        vm.setExtraFlowLimit(extra);
        vm.setUsedFlow(12D * GIB);
        vm.setOutFlow(0);
        vm.setBandwidth(100);
        vm.setPauseInfo("流量超限");
        return vm;
    }
}
