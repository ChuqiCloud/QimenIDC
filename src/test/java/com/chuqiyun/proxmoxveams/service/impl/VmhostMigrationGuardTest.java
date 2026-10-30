package com.chuqiyun.proxmoxveams.service.impl;

import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dto.VmIpParams;
import com.chuqiyun.proxmoxveams.dto.VmParams;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.VmIdReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VmhostMigrationGuardTest {
    @Test
    void reservedMigrationBlocksPowerDeleteReinstallAndConfigurationBeforeAnyPveOrTaskWork() {
        VmhostServiceImpl service = spy(new VmhostServiceImpl());
        VmIdReservationService reservations = mock(VmIdReservationService.class);
        ReflectionTestUtils.setField(service, "vmIdReservationService", reservations);
        ReflectionTestUtils.setField(service, "migrationExecutionLock", mock(com.chuqiyun.proxmoxveams.service.MigrationExecutionLock.class));
        when(reservations.lockAndIsHostReserved(any())).thenReturn(true);

        for (String action : new String[]{"start", "stop", "reboot", "resume", "unpause"}) {
            assertEquals(false, service.power(17, action, null).get("status"));
        }
        assertEquals(UnifiedResultCode.ERROR_INVALID_PARAM, service.deleteVm(17L).getResultCode());
        assertEquals(UnifiedResultCode.ERROR_INVALID_PARAM, service.deleteVmToRecycle(17L).getResultCode());
        assertEquals(UnifiedResultCode.ERROR_INVALID_PARAM, service.unDeleteVm(17L).getResultCode());
        assertEquals(UnifiedResultCode.ERROR_INVALID_PARAM, service.resetVmOs(17L, "debian", "pw", false).getResultCode());
        assertEquals(UnifiedResultCode.ERROR_INVALID_PARAM, service.resetVmPassword(17L, "pw").getResultCode());
        VmParams params = new VmParams();
        params.setHostid(17);
        assertEquals(UnifiedResultCode.ERROR_INVALID_PARAM, service.updateVm(params).getResultCode());
        VmIpParams ip = new VmIpParams();
        ip.setHostId(17);
        assertEquals(UnifiedResultCode.ERROR_INVALID_PARAM, service.updateVmIp(ip).getResultCode());
        assertEquals(UnifiedResultCode.ERROR_INVALID_PARAM, service.addVmIp(ip).getResultCode());
        assertEquals(UnifiedResultCode.ERROR_INVALID_PARAM, service.deleteVmIp(ip).getResultCode());
        assertEquals(UnifiedResultCode.ERROR_INVALID_PARAM, service.addVmDataDisk(17L, 5, "local").getResultCode());
        assertEquals(UnifiedResultCode.ERROR_INVALID_PARAM, service.deleteVmDataDisk(17L, "scsi1").getResultCode());
        assertEquals(UnifiedResultCode.ERROR_INVALID_PARAM, service.resizeVmDataDisk(17L, "scsi1", 10).getResultCode());
        Vmhost vm = new Vmhost();
        vm.setId(17);
        assertThrows(IllegalStateException.class, () -> service.addVmSnapShot(vm, "s1", false, "snapshot"));
        assertThrows(IllegalStateException.class, () -> service.deleteVmSnapShot(vm, "s1"));
        assertThrows(IllegalStateException.class, () -> service.rollbackVmSnapShot(vm, "s1"));
        assertThrows(IllegalStateException.class, () -> service.addVmBackup(vm, "stop", "zstd", "backup"));
        assertThrows(IllegalStateException.class, () -> service.rollbackVmBackup(vm, "backup", true, true));
        assertFalse(service.addVmhostNatWithResult("203.0.113.1", 10001, "172.16.0.4", 22, "tcp", 17).isSuccess());
        assertFalse(service.delVmhostNatWithResult("203.0.113.1", 10001, "172.16.0.4", 22, "tcp", 17).isSuccess());
        assertFalse(service.addVmhostVpcIpForward(17, "203.0.113.1", "172.16.0.4"));
        assertFalse(service.delVmhostVpcIpForwardWithResult(17, "203.0.113.1", "172.16.0.4").isSuccess());
        verify(service, never()).getById(any());
    }
}
