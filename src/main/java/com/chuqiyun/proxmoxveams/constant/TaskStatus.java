package com.chuqiyun.proxmoxveams.constant;

public class TaskStatus {
    /**
     * 等待执行
     */
    public static Integer WAITING = 0;

    /**
     * 执行中
     */
    public static Integer RUNNING = 1;

    /**
     * 执行成功
     */
    public static Integer SUCCESS = 2;

    /**
     * 执行失败
     */
    public static Integer FAILURE = 3;

    /**
     * 提示api接口调用时非异步成功
     */
    public static Integer API_NON_ASYNC_SUCCESS = 4;
}
