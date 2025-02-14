package com.chuqiyun.proxmoxveams.constant;

import com.chuqiyun.proxmoxveams.constant.TaskType; // 导入 TaskType 类

public class TaskTypeIdToName {

    public static String getTaskTypeName(Integer taskTypeId) {
        if (taskTypeId == null) {
            return "未知任务类型";
        }

        if (taskTypeId.equals(TaskType.CREATE_VM)) {
            return "创建虚拟机";
        } else if (taskTypeId.equals(TaskType.DELETE_VM)) {
            return "删除虚拟机";
        } else if (taskTypeId.equals(TaskType.UPDATE_VM)) {
            return "修改虚拟机";
        } else if (taskTypeId.equals(TaskType.IMPORT_SYSTEM_DISK)) {
            return "导入系统镜像";
        } else if (taskTypeId.equals(TaskType.UPDATE_SYSTEM_DISK_SIZE)) {
            return "修改系统盘大小";
        } else if (taskTypeId.equals(TaskType.DELETE_SYSTEM_DISK)) {
            return "删除系统盘";
        } else if (taskTypeId.equals(TaskType.CREATE_DATA_DISK)) {
            return "创建数据盘";
        } else if (taskTypeId.equals(TaskType.UPDATE_DATA_DISK_SIZE)) {
            return "修改数据盘大小";
        } else if (taskTypeId.equals(TaskType.DELETE_DATA_DISK)) {
            return "删除数据盘";
        } else if (taskTypeId.equals(TaskType.UPDATE_VM_NAME)) {
            return "修改虚拟机名称";
        } else if (taskTypeId.equals(TaskType.UPDATE_VM_MEMORY)) {
            return "修改虚拟机内存";
        } else if (taskTypeId.equals(TaskType.UPDATE_VM_BRIDGE)) {
            return "修改虚拟机网卡";
        } else if (taskTypeId.equals(TaskType.UPDATE_VM_OS)) {
            return "修改虚拟机操作系统";
        } else if (taskTypeId.equals(TaskType.UPDATE_VM_STORAGE)) {
            return "修改虚拟机存储";
        } else if (taskTypeId.equals(TaskType.UPDATE_VM_SOCKETS)) {
            return "修改虚拟机插槽数";
        } else if (taskTypeId.equals(TaskType.UPDATE_VM_THREADS)) {
            return "修改虚拟机线程数";
        } else if (taskTypeId.equals(TaskType.UPDATE_VM_CORES)) {
            return "修改虚拟机核心数";
        } else if (taskTypeId.equals(TaskType.UPDATE_VM_NESTED)) {
            return "修改虚拟机嵌套虚拟化";
        } else if (taskTypeId.equals(TaskType.UPDATE_VM_DESCRIPTION)) {
            return "修改虚拟机描述";
        } else if (taskTypeId.equals(TaskType.UPDATE_VM_COMMENT)) {
            return "修改虚拟机备注";
        } else if (taskTypeId.equals(TaskType.UPDATE_VM_BOOT)) {
            return "修改虚拟机启动顺序";
        } else if (taskTypeId.equals(TaskType.UPDATE_VM_CPU_LIMIT)) {
            return "修改虚拟机cpu限制";
        } else if (taskTypeId.equals(TaskType.UPDATE_VM_CPU_WEIGHT)) {
            return "修改虚拟机cpu权重";
        } else if (taskTypeId.equals(TaskType.START_VM)) {
            return "启动虚拟机";
        } else if (taskTypeId.equals(TaskType.STOP_VM)) {
            return "关闭虚拟机";
        } else if (taskTypeId.equals(TaskType.REBOOT_VM)) {
            return "重启虚拟机";
        } else if (taskTypeId.equals(TaskType.SUSPEND_VM)) {
            return "挂起虚拟机";
        } else if (taskTypeId.equals(TaskType.RESUME_VM)) {
            return "恢复虚拟机";
        } else if (taskTypeId.equals(TaskType.STOP_VM_FORCE)) {
            return "强制停止虚拟机";
        } else if (taskTypeId.equals(TaskType.PAUSE_VM)) {
            return "暂停虚拟机";
        } else if (taskTypeId.equals(TaskType.UNPAUSE_VM)) {
            return "恢复虚拟机";
        } else if (taskTypeId.equals(TaskType.REINSTALL_VM)) {
            return "重装系统";
        } else if (taskTypeId.equals(TaskType.RESET_PASSWORD)) {
            return "重置密码";
        } else if (taskTypeId.equals(TaskType.QOS_PAUSE)) {
            return "超流暂停";
        } else if (taskTypeId.equals(TaskType.UPDATE_IO_SYSTEM_DISK)) {
            return "配置系统盘IO限制";
        } else {
            return "未知任务类型";
        }
    }
}
