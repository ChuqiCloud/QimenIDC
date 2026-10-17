package com.chuqiyun.proxmoxveams.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskStatusIdToNameTest {
    @Test
    void resolvesEveryDeclaredStatus() {
        assertEquals("等待执行", TaskStatusIdToName.getTaskStatusName(TaskStatus.WAITING));
        assertEquals("执行中", TaskStatusIdToName.getTaskStatusName(TaskStatus.RUNNING));
        assertEquals("执行成功", TaskStatusIdToName.getTaskStatusName(TaskStatus.SUCCESS));
        assertEquals("执行失败", TaskStatusIdToName.getTaskStatusName(TaskStatus.FAILURE));
        assertEquals("接口同步成功", TaskStatusIdToName.getTaskStatusName(TaskStatus.API_NON_ASYNC_SUCCESS));
    }

    @Test
    void fallsBackForUnknownStatus() {
        assertEquals("未知状态", TaskStatusIdToName.getTaskStatusName(null));
        assertEquals("未知状态", TaskStatusIdToName.getTaskStatusName(99));
    }
}
