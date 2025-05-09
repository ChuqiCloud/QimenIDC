package com.chuqiyun.proxmoxveams.constant;

public class VmStatus {
    /**
     * 运行中
     */
    public static Integer RUNNING = 0;

    /**
     * 已关机
     */
    public static Integer SHUTDOWN = 1;

    /**
     * 挂起
     */
    public static Integer SUSPEND = 2;

    /**
     * 恢复中
     */
    public static Integer RESUMING = 3;

    /**
     * 暂停
     */
    public static Integer PAUSE = 4;

    /**
     * 到期
     */
    public static Integer EXPIRE = 5;

    /**
     * 创建中
     */
    public static Integer CREATING = 6;

    /**
     * 开机中
     */
    public static Integer STARTING = 7;

    /**
     * 关机中
     */
    public static Integer STOPPING = 8;

    /**
     * 停止中（强制关机中）
     */
    public static Integer STOPPING_FORCE = 9;

    /**
     * 挂起中
     */
    public static Integer SUSPENDING = 10;

    /**
     * 暂停中
     */
    public static Integer PAUSING = 11;

    /**
     * 重启中
     */
    public static Integer REBOOTING = 12;

    /**
     * 重装系统中
     */
    public static Integer REINSTALLING = 13;

    /**
     * 修改密码中
     */
    public static Integer RESET_PASSWORD = 14;
}
