package com.chuqiyun.proxmoxveams.service.impl;

import com.chuqiyun.proxmoxveams.dao.RescueVmStateDao;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.RescueVmState;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RescueServiceEnableTest {
    @Test
    void insertsCompleteStateInOneOperation() {
        RescueServiceImpl service = new RescueServiceImpl();
        RescueVmStateDao states = mock(RescueVmStateDao.class);
        VmhostService vmhosts = mock(VmhostService.class);
        MasterService masters = mock(MasterService.class);
        TaskExecutor executor = mock(TaskExecutor.class);

        Vmhost vmhost = new Vmhost();
        vmhost.setId(123);
        vmhost.setNodeid(7);
        vmhost.setVmid(100);
        Master node = new Master();
        node.setId(7);
        node.setNodeName("pve01");
        when(states.selectOne(any())).thenReturn(null);
        when(vmhosts.getById(123)).thenReturn(vmhost);
        when(masters.getById(7)).thenReturn(node);
        doAnswer(invocation -> {
            RescueVmState state = invocation.getArgument(0);
            state.setId(1L);
            return 1;
        }).when(states).insert(any(RescueVmState.class));

        ReflectionTestUtils.setField(service, "rescueVmStateDao", states);
        ReflectionTestUtils.setField(service, "vmhostService", vmhosts);
        ReflectionTestUtils.setField(service, "masterService", masters);
        ReflectionTestUtils.setField(service, "workflowExecutor", executor);

        RescueVmState result = service.enable(123, "windows-wepe", "local");

        verify(states).insert(any(RescueVmState.class));
        assertEquals(1L, result.getId());
        assertEquals(7, result.getNodeId());
        assertEquals(100, result.getVmid());
        assertEquals("windows-wepe", result.getImageId());
        assertNotNull(result.getImageUrl());
        assertNotNull(result.getFileName());
        assertEquals("ENABLING", result.getStatus());
        assertNotNull(result.getUpdateTime());
    }
}
