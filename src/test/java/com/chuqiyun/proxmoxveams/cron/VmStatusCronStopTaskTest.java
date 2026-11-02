package com.chuqiyun.proxmoxveams.cron;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.chuqiyun.proxmoxveams.constant.TaskType.STOP_VM_FORCE;
import static com.chuqiyun.proxmoxveams.constant.TaskType.UPDATE_VM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @Author: 星禾
 * @Description: 强制关机使用旧快照时，不得覆盖等待期间已提交的改配字段。
 * @DateTime: 2026/11/2 11:24
 */
class VmStatusCronStopTaskTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stopCompletionPreservesConfigurationCommittedAfterVmSnapshotWasRead(boolean hasUpdateParent) throws Exception {
        MasterService masters = mock(MasterService.class);
        TaskService tasks = mock(TaskService.class);
        VmhostService hosts = mock(VmhostService.class);
        ProxmoxApiUtil pve = mock(ProxmoxApiUtil.class);
        VmStatusCron cron = new VmStatusCron() {
            @Override
            protected ProxmoxApiUtil newProxmoxApi() {
                return pve;
            }
        };
        ReflectionTestUtils.setField(cron, "masterService", masters);
        ReflectionTestUtils.setField(cron, "taskService", tasks);
        ReflectionTestUtils.setField(cron, "vmhostService", hosts);
        Master node = new Master();
        node.setId(3);
        node.setNodeName("pve");
        when(masters.getById(3)).thenReturn(node);
        when(masters.getMasterCookieMap(3)).thenReturn(new HashMap<>());
        Task task = new Task();
        task.setId(9);
        task.setType(STOP_VM_FORCE);
        task.setStatus(0);
        task.setHostid(17);
        task.setNodeid(3);
        task.setVmid(101);
        if (hasUpdateParent) {
            task.setParams(new HashMap<>(Map.of("parentUpdateTaskId", 8)));
            Task parent = new Task();
            parent.setId(8);
            parent.setType(UPDATE_VM);
            parent.setStatus(1);
            parent.setHostid(17);
            parent.setNodeid(3);
            parent.setVmid(101);
            when(tasks.getById(8)).thenReturn(parent);
        }
        when(tasks.getOne(any())).thenReturn(task);
        when(tasks.update(any())).thenReturn(true);
        Vmhost snapshot = vm();
        Vmhost persisted = vm();
        when(hosts.getById(17)).thenReturn(snapshot);
        when(hosts.updateById(any(Vmhost.class))).thenAnswer(invocation -> {
            Vmhost write = invocation.getArgument(0);
            if (write.getStatus() != null) persisted.setStatus(write.getStatus());
            if (write.getMemory() != null) persisted.setMemory(write.getMemory());
            if (write.getFlowLimit() != null) persisted.setFlowLimit(write.getFlowLimit());
            if (write.getExtraFlowLimit() != null) persisted.setExtraFlowLimit(write.getExtraFlowLimit());
            if (write.getUsedFlow() != null) persisted.setUsedFlow(write.getUsedFlow());
            return true;
        });
        when(pve.postNodeApi(eq(node), any(), eq("/nodes/pve/qemu/101/status/stop"), any()))
                .thenAnswer(invocation -> {
                    // PVE 已停止，改配线程在本关机任务写回状态前提交新配置。
                    persisted.setMemory(4096);
                    persisted.setFlowLimit(30L * GIB);
                    persisted.setExtraFlowLimit(8L * GIB);
                    persisted.setUsedFlow(7D * GIB);
                    return new JSONObject();
                });

        cron.stopVmNow();

        assertEquals(1, persisted.getStatus());
        assertEquals(4096, persisted.getMemory());
        assertEquals(30L * GIB, persisted.getFlowLimit());
        assertEquals(8L * GIB, persisted.getExtraFlowLimit());
        assertEquals(7D * GIB, persisted.getUsedFlow());
        assertEquals(2, task.getStatus());
    }

    @Test
    void canceledQueuedTaskCannotBeClaimedFromAnEarlierRead() {
        TaskService tasks = mock(TaskService.class);
        MasterService masters = mock(MasterService.class);
        VmhostService hosts = mock(VmhostService.class);
        VmStatusCron cron = cron(tasks, masters, hosts);
        Task task = new Task();
        task.setId(9);
        task.setStatus(0);
        when(tasks.getOne(any())).thenReturn(task);
        // 改配失败已把持久化状态改为失败，执行器手中的排队快照认领失败。
        when(tasks.update(any())).thenReturn(false);

        cron.stopVmNow();

        assertEquals(0, task.getStatus());
        verifyNoInteractions(masters, hosts);
    }

    @ParameterizedTest
    @ValueSource(ints = {9, 13, 1})
    void shutdownWithFailedParentIsCanceledBeforePveCallAndOnlyClearsItsPendingStatus(int initialVmStatus) {
        TaskService tasks = mock(TaskService.class);
        MasterService masters = mock(MasterService.class);
        VmhostService hosts = mock(VmhostService.class);
        VmStatusCron cron = cron(tasks, masters, hosts);
        Task task = new Task();
        task.setId(9);
        task.setStatus(0);
        task.setType(STOP_VM_FORCE);
        task.setHostid(17);
        task.setNodeid(3);
        task.setVmid(101);
        task.setParams(new HashMap<>(Map.of("parentUpdateTaskId", 8)));
        Task parent = new Task();
        parent.setId(8);
        parent.setStatus(3);
        parent.setType(UPDATE_VM);
        parent.setHostid(17);
        parent.setNodeid(3);
        parent.setVmid(101);
        when(tasks.getOne(any())).thenReturn(task);
        when(tasks.update(any())).thenReturn(true);
        when(tasks.getById(8)).thenReturn(parent);
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("create table vmhost(id int primary key, status int)");
        jdbc.update("insert into vmhost values(17,?)", initialVmStatus);
        when(hosts.update(any())).thenAnswer(invocation -> updateVmStatus(jdbc, invocation.getArgument(0)));

        try {
            cron.stopVmNow();

            assertEquals(3, task.getStatus());
            assertEquals(initialVmStatus == 9 ? 0 : initialVmStatus,
                    jdbc.queryForObject("select status from vmhost where id=17", Integer.class));
            verifyNoInteractions(masters);
        } finally {
            jdbc.execute("drop all objects");
        }
    }

    private boolean updateVmStatus(JdbcTemplate jdbc, Wrapper<?> wrapper) {
        UpdateWrapper<?> values = (UpdateWrapper<?>) wrapper;
        String sql = "update vmhost set " + values.getSqlSet() + " " + values.getCustomSqlSegment();
        var parameters = new ArrayList<Object>();
        String prepared = Pattern.compile("#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}").matcher(sql)
                .replaceAll(match -> {
                    parameters.add(values.getParamNameValuePairs().get(match.group(1)));
                    return "?";
                });
        return jdbc.update(prepared, parameters.toArray()) == 1;
    }

    @ParameterizedTest
    @ValueSource(strings = {"8", "invalid-parent"})
    void missingOrInvalidParentCancelsShutdownWithoutAssumingVmIsRunning(String parentId) {
        TaskService tasks = mock(TaskService.class);
        MasterService masters = mock(MasterService.class);
        VmhostService hosts = mock(VmhostService.class);
        VmStatusCron cron = cron(tasks, masters, hosts);
        Task task = new Task();
        task.setId(9);
        task.setStatus(0);
        task.setParams(new HashMap<>(Map.of("parentUpdateTaskId", parentId)));
        when(tasks.getOne(any())).thenReturn(task);
        when(tasks.update(any())).thenReturn(true);

        cron.stopVmNow();

        assertEquals(3, task.getStatus());
        verifyNoInteractions(masters, hosts);
    }

    private VmStatusCron cron(TaskService tasks, MasterService masters, VmhostService hosts) {
        VmStatusCron cron = new VmStatusCron();
        ReflectionTestUtils.setField(cron, "taskService", tasks);
        ReflectionTestUtils.setField(cron, "masterService", masters);
        ReflectionTestUtils.setField(cron, "vmhostService", hosts);
        return cron;
    }

    private Vmhost vm() {
        Vmhost vm = new Vmhost();
        vm.setId(17);
        vm.setNodeid(3);
        vm.setVmid(101);
        vm.setStatus(9);
        vm.setMemory(1024);
        vm.setFlowLimit(10L * GIB);
        vm.setExtraFlowLimit(2L * GIB);
        vm.setUsedFlow(3D * GIB);
        return vm;
    }

    private static final long GIB = 1024L * 1024 * 1024;
}
