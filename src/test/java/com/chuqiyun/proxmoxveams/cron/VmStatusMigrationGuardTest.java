package com.chuqiyun.proxmoxveams.cron;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmIdReservationService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class VmStatusMigrationGuardTest {
    @Test
    void expireScannerDoesNotQueuePowerTaskOrChangeMigratingVm() {
        VmStatusCron cron = new VmStatusCron();
        VmhostService hosts = mock(VmhostService.class);
        TaskService tasks = mock(TaskService.class);
        VmIdReservationService reservations = mock(VmIdReservationService.class);
        ReflectionTestUtils.setField(cron, "vmhostService", hosts);
        ReflectionTestUtils.setField(cron, "taskService", tasks);
        ReflectionTestUtils.setField(cron, "vmIdReservationService", reservations);
        Vmhost vm = new Vmhost();
        vm.setId(11);
        vm.setStatus(0);
        Page<Vmhost> page = new Page<>(1, 10);
        page.setRecords(List.of(vm));
        Page<Vmhost> end = new Page<>(2, 10);
        when(hosts.selectPage(anyInt(), anyInt(), any())).thenReturn(page, end);
        when(reservations.isHostReserved(11)).thenReturn(true);

        cron.expireCron();

        verify(tasks, never()).save(any(Task.class));
        verify(hosts, never()).updateById(any(Vmhost.class));
    }

    @Test
    void oldPowerTaskCannotRestartSourceVmWhileMigrationReservationExists() {
        VmStatusCron cron = new VmStatusCron();
        MasterService nodes = mock(MasterService.class);
        TaskService tasks = mock(TaskService.class);
        VmIdReservationService reservations = mock(VmIdReservationService.class);
        ReflectionTestUtils.setField(cron, "masterService", nodes);
        ReflectionTestUtils.setField(cron, "taskService", tasks);
        ReflectionTestUtils.setField(cron, "vmIdReservationService", reservations);
        Task task = new Task();
        task.setHostid(11);
        task.setStatus(0);
        when(tasks.getOne(any())).thenReturn(task);
        when(reservations.isHostReserved(11)).thenReturn(true);

        cron.startVm();

        assertEquals(3, task.getStatus());
        verifyNoInteractions(nodes);
        verify(tasks).updateById(task);
    }
}
