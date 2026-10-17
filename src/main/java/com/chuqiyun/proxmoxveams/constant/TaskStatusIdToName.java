package com.chuqiyun.proxmoxveams.constant;

/**
 * 任务状态码转可读名称，供管理端接口输出。
 *
 * @author CodeBuddy
 * @date 2026/10/17
 */
public class TaskStatusIdToName {

    public static String getTaskStatusName(Integer taskStatusId) {
        if (taskStatusId == null) {
            return "未知状态";
        }
        if (taskStatusId.equals(TaskStatus.WAITING)) {
            return "等待执行";
        } else if (taskStatusId.equals(TaskStatus.RUNNING)) {
            return "执行中";
        } else if (taskStatusId.equals(TaskStatus.SUCCESS)) {
            return "执行成功";
        } else if (taskStatusId.equals(TaskStatus.FAILURE)) {
            return "执行失败";
        } else if (taskStatusId.equals(TaskStatus.API_NON_ASYNC_SUCCESS)) {
            return "接口同步成功";
        }
        return "未知状态";
    }
}
