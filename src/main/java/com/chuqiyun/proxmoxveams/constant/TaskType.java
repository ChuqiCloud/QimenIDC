package com.chuqiyun.proxmoxveams.constant;

/**
 * @author mryunqi
 * @date 2023/6/30
 */
public class TaskType {
    /**
     * 创建虚拟机
     */
    public static Integer CREATE_VM = 0;
    /**
     * 删除虚拟机
     */
    public static Integer DELETE_VM = 1;
    /**
     * 修改虚拟机
     */
    public static Integer UPDATE_VM = 2;
    /**
     * 导入系统镜像
     */
    public static Integer IMPORT_SYSTEM_DISK = 3;
    /**
     * 修改系统盘大小
     */
    public static Integer UPDATE_SYSTEM_DISK_SIZE = 4;
    /**
     * 删除系统盘
     */
    public static Integer DELETE_SYSTEM_DISK = 5;
    /**
     * 创建数据盘
     */
    public static Integer CREATE_DATA_DISK = 6;
    /**
     * 修改数据盘大小
     */
    public static Integer UPDATE_DATA_DISK_SIZE = 7;
    /**
     * 删除数据盘
     */
    public static Integer DELETE_DATA_DISK = 8;
    /**
     * 修改虚拟机名称
     */
    public static Integer UPDATE_VM_NAME = 9;
    /**
     * 修改虚拟机内存
     */
    public static Integer UPDATE_VM_MEMORY = 10;
    /**
     * 修改虚拟机网卡
     */
    public static Integer UPDATE_VM_BRIDGE = 12;
    /**
     * 修改虚拟机操作系统
     */
    public static Integer UPDATE_VM_OS = 13;
    /**
     * 修改虚拟机存储
     */
    public static Integer UPDATE_VM_STORAGE = 14;
    /**
     * 修改虚拟机插槽数
     */
    public static Integer UPDATE_VM_SOCKETS = 15;
    /**
     * 修改虚拟机线程数
     */
    public static Integer UPDATE_VM_THREADS = 16;
    /**
     * 修改虚拟机核心数
     */
    public static Integer UPDATE_VM_CORES = 17;
    /**
     * 修改虚拟机嵌套虚拟化
     */
    public static Integer UPDATE_VM_NESTED = 18;
    /**
     * 修改虚拟机描述
     */
    public static Integer UPDATE_VM_DESCRIPTION = 19;
    /**
     * 修改虚拟机备注
     */
    public static Integer UPDATE_VM_COMMENT = 20;
    /**
     * 修改虚拟机启动顺序
     */
    public static Integer UPDATE_VM_BOOT = 21;
    /**
     * 修改虚拟机cpu限制
     */
    public static Integer UPDATE_VM_CPU_LIMIT = 22;
    /**
     * 修改虚拟机cpu权重
     */
    public static Integer UPDATE_VM_CPU_WEIGHT = 23;
    /**
     * 启动虚拟机
     */
    public static Integer START_VM = 24;
    /**
     * 关闭虚拟机
     */
    public static Integer STOP_VM = 25;
    /**
     * 重启虚拟机
     */
    public static Integer REBOOT_VM = 26;
    /**
     * 挂起虚拟机
     */
    public static Integer SUSPEND_VM = 27;
    /**
     * 恢复虚拟机
     */
    public static Integer RESUME_VM = 28;
    /**
     * 强制停止虚拟机
     */
    public static Integer STOP_VM_FORCE = 29;
    /**
     * 暂停虚拟机
     */
    public static Integer PAUSE_VM = 30;
    /**
     * 恢复虚拟机
     */
    public static Integer UNPAUSE_VM = 31;
    /**
     * 重装系统
     */
    public static Integer REINSTALL_VM = 32;
    /**
     * 重置密码
     */
    public static Integer RESET_PASSWORD = 33;
    /**
     * 超流暂停
     */
    public static Integer QOS_PAUSE = 34;
    /**
     * 配置系统盘IO限制
     */
    public static Integer UPDATE_IO_SYSTEM_DISK = 35;

}
