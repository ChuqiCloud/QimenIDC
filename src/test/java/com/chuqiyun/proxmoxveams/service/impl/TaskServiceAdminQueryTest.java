package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.constant.TaskStatus;
import com.chuqiyun.proxmoxveams.constant.TaskType;
import com.chuqiyun.proxmoxveams.dto.TaskItemDto;
import com.chuqiyun.proxmoxveams.dto.TaskQueryDto;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 管理端任务查询必须输出可读状态，且不能把任务参数里的凭据带出接口。
 */
class TaskServiceAdminQueryTest {

    private TaskServiceImpl service;

    @BeforeEach
    void createServiceWithRelatedNameQueries() {
        service = spy(new TaskServiceImpl());
        MasterService masterService = mock(MasterService.class);
        VmhostService vmhostService = mock(VmhostService.class);
        when(masterService.list(any(Wrapper.class))).thenReturn(List.of());
        when(vmhostService.list(any(Wrapper.class))).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "masterService", masterService);
        ReflectionTestUtils.setField(service, "vmhostService", vmhostService);
    }

    @Test
    void listKeepsProgressButDropsCredentials() {
        Page<Task> page = new Page<>(1, 20);
        page.setTotal(1);
        page.setRecords(List.of(migrationTask()));
        doReturn(page).when(service).page(any(Page.class), any(Wrapper.class));

        TaskQueryDto result = service.getTaskPageForAdmin(new TaskQueryDto());
        TaskItemDto item = result.getRecords().get(0);

        assertEquals(1L, result.getTotal());
        assertEquals("离线迁移虚拟机", item.getTypeName());
        assertEquals("执行中", item.getStatusName());
        assertEquals(40, item.getProgress());
        assertEquals("MIGRATING", item.getStage());
        assertTrue(item.getParams().containsKey("progress"));
        assertFalse(item.getParams().containsKey("password"));
        assertFalse(item.getParams().containsKey("token"));
        assertEquals(4000L, item.getDuration());
    }

    @Test
    void detailReturnsNullWhenTaskMissing() {
        doReturn(null).when(service).getById((Serializable) 404);

        assertNull(service.getTaskDetailForAdmin(404));
        assertNull(service.getTaskDetailForAdmin(null));
    }

    @Test
    void detailReportsFinishedTaskWithoutReportedEndTime() {
        Task task = migrationTask();
        task.setParams(new HashMap<>());
        task.setStatus(TaskStatus.FAILURE);
        task.setError("迁移超时");
        doReturn(task).when(service).getById((Serializable) 7);

        TaskItemDto detail = service.getTaskDetailForAdmin(7);

        assertEquals("执行失败", detail.getStatusName());
        assertEquals("迁移超时", detail.getError());
        assertNull(detail.getDuration());
        assertNull(detail.getUpdateTime());
    }

    @Test
    void invalidUpdateTimesDoNotProduceNegativeDurations() {
        for (Object updateTime : List.of(0L, -1L, "invalid", 1_600_000_000_000L)) {
            Task task = migrationTask();
            task.getParams().put("updateTime", updateTime);
            task.setStatus(TaskStatus.SUCCESS);
            doReturn(task).when(service).getById((Serializable) 7);

            assertNull(service.getTaskDetailForAdmin(7).getDuration());
        }
    }

    @Test
    void runningTaskWithoutUpdateTimeUsesElapsedWallTime() {
        Task task = migrationTask();
        task.getParams().remove("updateTime");
        task.setCreateDate(System.currentTimeMillis() - 1000L);
        doReturn(task).when(service).getById((Serializable) 7);

        TaskItemDto detail = service.getTaskDetailForAdmin(7);

        assertNull(detail.getUpdateTime());
        assertTrue(detail.getDuration() >= 1000L);
    }

    private Task migrationTask() {
        Map<Object, Object> params = new HashMap<>();
        params.put("progress", 40);
        params.put("stage", "MIGRATING");
        params.put("updateTime", 1_700_000_004_000L);
        params.put("password", "plain-secret");
        params.put("token", "agent-token");

        Task task = new Task();
        task.setId(7);
        task.setNodeid(1);
        task.setHostid(2);
        task.setVmid(100);
        task.setType(TaskType.MIGRATE_VM);
        task.setStatus(TaskStatus.RUNNING);
        task.setCreateDate(1_700_000_000_000L);
        task.setParams(params);
        return task;
    }
}
