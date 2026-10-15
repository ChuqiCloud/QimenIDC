package com.chuqiyun.proxmoxveams.cron;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowDataCronTest {
    private VmhostService vmhostService;
    private MasterService masterService;
    private FlowDataCron cron;

    @BeforeEach
    void setUp() {
        vmhostService = mock(VmhostService.class);
        masterService = mock(MasterService.class);
        cron = new FlowDataCron();
        ReflectionTestUtils.setField(cron, "vmhostService", vmhostService);
        ReflectionTestUtils.setField(cron, "masterService", masterService);
    }

    @Test
    void limitingTrafficDoesNotWriteBackTheOldPackageAllowance() {
        Vmhost vmhost = limitedVmhost();
        vmhost.setStatus(0);
        vmhost.setFlowLimit(1L);
        vmhost.setExtraFlowLimit(1L);
        vmhost.setUsedFlow(3D);
        Page<Vmhost> page = new Page<>(1, 100, 1);
        page.setRecords(List.of(vmhost));
        when(vmhostService.selectPage(org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(100), any())).thenReturn(page);
        when(masterService.isNodeOnline(3)).thenReturn(true);

        cron.vmFlowLimitCron();

        ArgumentCaptor<UpdateWrapper<Vmhost>> update = updateCaptor();
        verify(vmhostService).update(update.capture());
        assertTrue(update.getValue().getSqlSet().contains("status="));
        assertTrue(update.getValue().getSqlSet().contains("pause_info="));
        assertFalse(update.getValue().getSqlSet().contains("extra_flow_limit"));
        verify(vmhostService, never()).updateById(any(Vmhost.class));
    }

    @Test
    void restoringBandwidthOnlyWritesStateAfterTheNodeAcceptsIt() {
        Vmhost vmhost = limitedVmhost();
        vmhost.setOutFlow(1024);
        vmhost.setBandwidth(80);
        List<String> operations = new ArrayList<>();
        when(vmhostService.changeVmHostBandWidth(vmhost, "10.000")).thenAnswer(invocation -> {
            operations.add("bandwidth");
            vmhost.setNet0("virtio=AA:BB:CC:DD:EE:FF,bridge=vmbr0,rate=10.000");
            return true;
        });
        when(vmhostService.update(any())).thenAnswer(invocation -> {
            operations.add("state");
            return true;
        });

        ReflectionTestUtils.invokeMethod(cron, "outFlowIsZero", vmhost);

        assertEquals(List.of("bandwidth", "state"), operations);
        ArgumentCaptor<UpdateWrapper<Vmhost>> update = updateCaptor();
        verify(vmhostService).update(update.capture());
        assertFalse(update.getValue().getSqlSet().contains("extra_flow_limit"));
        assertTrue(update.getValue().getSqlSet().contains("net0="));
        verify(vmhostService, never()).updateById(any(Vmhost.class));
    }

    @Test
    void failedBandwidthRestoreKeepsTheVmLimitedForRetry() {
        Vmhost vmhost = limitedVmhost();
        vmhost.setOutFlow(1024);
        vmhost.setBandwidth(80);
        when(vmhostService.changeVmHostBandWidth(vmhost, "10.000")).thenReturn(false);

        ReflectionTestUtils.invokeMethod(cron, "outFlowIsZero", vmhost);

        assertEquals(15, vmhost.getStatus());
        verify(vmhostService, never()).update(any());
        verify(vmhostService, never()).updateById(any(Vmhost.class));
    }

    @Test
    void missingResumeResponseDoesNotClearThePauseReason() {
        Vmhost vmhost = limitedVmhost();

        ReflectionTestUtils.invokeMethod(cron, "outFlowIsZero", vmhost);

        verify(vmhostService, never()).update(any());
    }

    @Test
    void queuedResumeOnlyClearsThePauseReason() {
        Vmhost vmhost = limitedVmhost();
        when(vmhostService.power(17, "unpause", null)).thenReturn(new java.util.HashMap<>(Map.of("status", true)));

        ReflectionTestUtils.invokeMethod(cron, "outFlowIsZero", vmhost);

        ArgumentCaptor<UpdateWrapper<Vmhost>> update = updateCaptor();
        verify(vmhostService).update(update.capture());
        assertTrue(update.getValue().getSqlSet().startsWith("pause_info="));
        assertFalse(update.getValue().getSqlSet().contains("status="));
        assertFalse(update.getValue().getSqlSet().contains("extra_flow_limit"));
    }

    private Vmhost limitedVmhost() {
        Vmhost vmhost = new Vmhost();
        vmhost.setId(17);
        vmhost.setNodeid(3);
        vmhost.setStatus(15);
        vmhost.setOutFlow(0);
        vmhost.setExtraFlowLimit(100L);
        return vmhost;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<UpdateWrapper<Vmhost>> updateCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(UpdateWrapper.class);
    }
}
