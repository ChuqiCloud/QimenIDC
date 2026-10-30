package com.chuqiyun.proxmoxveams.service.impl;

import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.NatForwardRuleService;
import com.chuqiyun.proxmoxveams.service.NatSyncStateService;
import com.chuqiyun.proxmoxveams.service.VmIdReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.*;

class NatForwardMigrationGuardTest {
    @Test
    void authoritativeSyncAndVpcAliasReconcileSkipNodesInMigration() {
        NatForwardSyncServiceImpl service = new NatForwardSyncServiceImpl();
        MasterService masters = mock(MasterService.class);
        VmIdReservationService reservations = mock(VmIdReservationService.class);
        NatForwardRuleService rules = mock(NatForwardRuleService.class);
        NatSyncStateService states = mock(NatSyncStateService.class);
        ReflectionTestUtils.setField(service, "masterService", masters);
        ReflectionTestUtils.setField(service, "vmIdReservationService", reservations);
        ReflectionTestUtils.setField(service, "natForwardRuleService", rules);
        ReflectionTestUtils.setField(service, "natSyncStateService", states);
        Master node = new Master();
        node.setId(13);
        node.setHost("192.0.2.13");
        node.setControllerPort(7600);
        node.setPublicIpInterface("vmbr0");
        node.setPublicIpMode("l2_alias");
        when(masters.list()).thenReturn(List.of(node));
        when(reservations.isNodeInMigration(13)).thenReturn(true);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(reservations).withAllocationLock(any(Runnable.class));

        service.syncAllNodes();

        verifyNoInteractions(rules, states);
    }
}
