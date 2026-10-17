package com.chuqiyun.proxmoxveams.constant;

import com.chuqiyun.proxmoxveams.constant.TaskType; // 导入 TaskType 类

import java.util.HashMap;
import java.util.Map;

public class TaskTypeIdToName {

    private static final Map<Integer, String> TASK_TYPE_NAMES = new HashMap<>();

    static {
        TASK_TYPE_NAMES.put(TaskType.CREATE_VM, "创建虚拟机");
        TASK_TYPE_NAMES.put(TaskType.DELETE_VM, "删除虚拟机");
        TASK_TYPE_NAMES.put(TaskType.UPDATE_VM, "修改虚拟机");
        TASK_TYPE_NAMES.put(TaskType.IMPORT_SYSTEM_DISK, "导入系统镜像");
        TASK_TYPE_NAMES.put(TaskType.UPDATE_SYSTEM_DISK_SIZE, "修改系统盘大小");
        TASK_TYPE_NAMES.put(TaskType.DELETE_SYSTEM_DISK, "删除系统盘");
        TASK_TYPE_NAMES.put(TaskType.CREATE_DATA_DISK, "创建数据盘");
        TASK_TYPE_NAMES.put(TaskType.UPDATE_DATA_DISK_SIZE, "修改数据盘大小");
        TASK_TYPE_NAMES.put(TaskType.DELETE_DATA_DISK, "删除数据盘");
        TASK_TYPE_NAMES.put(TaskType.UPDATE_VM_NAME, "修改虚拟机名称");
        TASK_TYPE_NAMES.put(TaskType.UPDATE_VM_MEMORY, "修改虚拟机内存");
        TASK_TYPE_NAMES.put(TaskType.UPDATE_VM_BRIDGE, "修改虚拟机网卡");
        TASK_TYPE_NAMES.put(TaskType.UPDATE_VM_OS, "修改虚拟机操作系统");
        TASK_TYPE_NAMES.put(TaskType.UPDATE_VM_STORAGE, "修改虚拟机存储");
        TASK_TYPE_NAMES.put(TaskType.UPDATE_VM_SOCKETS, "修改虚拟机插槽数");
        TASK_TYPE_NAMES.put(TaskType.UPDATE_VM_THREADS, "修改虚拟机线程数");
        TASK_TYPE_NAMES.put(TaskType.UPDATE_VM_CORES, "修改虚拟机核心数");
        TASK_TYPE_NAMES.put(TaskType.UPDATE_VM_NESTED, "修改虚拟机嵌套虚拟化");
        TASK_TYPE_NAMES.put(TaskType.UPDATE_VM_DESCRIPTION, "修改虚拟机描述");
        TASK_TYPE_NAMES.put(TaskType.UPDATE_VM_COMMENT, "修改虚拟机备注");
        TASK_TYPE_NAMES.put(TaskType.UPDATE_VM_BOOT, "修改虚拟机启动顺序");
        TASK_TYPE_NAMES.put(TaskType.UPDATE_VM_CPU_LIMIT, "修改虚拟机cpu限制");
        TASK_TYPE_NAMES.put(TaskType.UPDATE_VM_CPU_WEIGHT, "修改虚拟机cpu权重");
        TASK_TYPE_NAMES.put(TaskType.START_VM, "启动虚拟机");
        TASK_TYPE_NAMES.put(TaskType.STOP_VM, "关闭虚拟机");
        TASK_TYPE_NAMES.put(TaskType.REBOOT_VM, "重启虚拟机");
        TASK_TYPE_NAMES.put(TaskType.SUSPEND_VM, "挂起虚拟机");
        TASK_TYPE_NAMES.put(TaskType.RESUME_VM, "恢复虚拟机");
        TASK_TYPE_NAMES.put(TaskType.STOP_VM_FORCE, "强制停止虚拟机");
        TASK_TYPE_NAMES.put(TaskType.PAUSE_VM, "暂停虚拟机");
        TASK_TYPE_NAMES.put(TaskType.UNPAUSE_VM, "恢复虚拟机");
        TASK_TYPE_NAMES.put(TaskType.REINSTALL_VM, "重装系统");
        TASK_TYPE_NAMES.put(TaskType.RESET_PASSWORD, "重置密码");
        TASK_TYPE_NAMES.put(TaskType.QOS_PAUSE, "超流暂停");
        TASK_TYPE_NAMES.put(TaskType.UPDATE_IO_SYSTEM_DISK, "配置系统盘IO限制");
        TASK_TYPE_NAMES.put(TaskType.IMPORT_SYSTEM_DISK_NEW, "导入系统镜像并挂载");
        TASK_TYPE_NAMES.put(TaskType.IP_CHANGE_RESTART_VM, "IP变更异步重启虚拟机");
        TASK_TYPE_NAMES.put(TaskType.APPLY_WINDOWS_VM_IP, "应用Windows虚拟机IP");
        TASK_TYPE_NAMES.put(TaskType.RUN_QEMU_INIT_SCRIPT, "执行QEMU初始化脚本");
        TASK_TYPE_NAMES.put(TaskType.MIGRATE_VM, "离线迁移虚拟机");
    }

    /**
     * 获取任务类型名称，兼容历史数字类型任务（原为 if-else 链，缺失类型返回未知）。
     */
    public static String getTaskTypeName(Integer taskTypeId) {
        if (taskTypeId == null) {
            return "未知任务类型";
        }
        return TASK_TYPE_NAMES.getOrDefault(taskTypeId, "未知任务类型");
    }
}
