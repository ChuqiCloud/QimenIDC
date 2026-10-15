package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.chuqiyun.proxmoxveams.entity.Flowdata;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.FlowdataService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FlowServiceQuotaTest {
    private FlowServiceImpl service;
    private VmhostService vmhostService;

    @BeforeEach
    void setUp() {
        service = new FlowServiceImpl();
        FlowdataService flowdataService = mock(FlowdataService.class);
        vmhostService = mock(VmhostService.class);
        ReflectionTestUtils.setField(service, "flowdataService", flowdataService);
        ReflectionTestUtils.setField(service, "vmhostService", vmhostService);
        Flowdata sample = new Flowdata();
        sample.setId(9);
        sample.setHostid(17);
        sample.setUsedFlow(10D);
        sample.setInFlow(4D);
        sample.setOutFlow(6D);
        when(flowdataService.list(any(Wrapper.class))).thenReturn(List.of(sample), Collections.emptyList());
        when(flowdataService.update(any(Wrapper.class))).thenReturn(true);
    }

    @Test
    void usageSamplerCannotOverwriteQuotaPurchasedAfterSnapshotWasRead() {
        Vmhost staleSnapshot = new Vmhost();
        staleSnapshot.setId(17);
        staleSnapshot.setExtraFlowLimit(0L);
        when(vmhostService.update(any(Wrapper.class))).thenReturn(true);

        assertTrue(service.syncVmFlowdata(null, staleSnapshot, false));

        ArgumentCaptor<Wrapper> writes = ArgumentCaptor.forClass(Wrapper.class);
        verify(vmhostService).update(writes.capture());
        String sqlSet = ((UpdateWrapper<?>) writes.getValue()).getSqlSet();
        assertTrue(sqlSet.contains("used_flow = COALESCE(used_flow, 0) + 10.0"));
        assertTrue(sqlSet.contains("used_in_flow = COALESCE(used_in_flow, 0) + 4.0"));
        assertTrue(sqlSet.contains("used_out_flow = COALESCE(used_out_flow, 0) + 6.0"));
        assertFalse(sqlSet.contains("extra_flow_limit"));
        assertFalse(sqlSet.contains("status"));
        verify(vmhostService, never()).updateById(any(Vmhost.class));
    }

    @Test
    void counterPersistenceFailureAbortsTransactionalSync() {
        Vmhost snapshot = new Vmhost();
        snapshot.setId(17);
        when(vmhostService.update(any(Wrapper.class))).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.syncVmFlowdata(null, snapshot, false));
    }
}
