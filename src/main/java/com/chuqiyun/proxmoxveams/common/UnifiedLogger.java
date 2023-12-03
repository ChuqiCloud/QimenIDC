package com.chuqiyun.proxmoxveams.common;

import lombok.extern.slf4j.Slf4j;

/**
 * @author mryunqi
 * @date 2023/8/6
 */
@Slf4j
public class UnifiedLogger {

    /**
     * 日志类型
     */
    public enum LogType {
        /**
         * API日志
         */
        API("[API]"),
        /**
         * 任务日志
         */
        TASK_CREATE_VM("[Task-CreateVm]"),
        TASK_IMPORT_SYSTEM_DISK("[Task-ImportSystemDisk]"),
        TASK_IMPORT_DATA_DISK("[Task-ImportDataDisk]"),
        // updateSystemDisk
        TASK_UPDATE_SYSTEM_DISK("[Task-UpdateSystemDisk]"),
        TASK_CREATE_DATA_DISK("[Task-CreateDataDisk]"),
        TASK_UPDATE_BOOT("[Task-UpdateBoot]"),
        // 重置系统
        TASK_RESET_SYSTEM("[Task-ResetSystem]"),
        // 重置密码
        TASK_RESET_PASSWORD("[Task-ResetPassword]"),
        // 开机任务
        TASK_START_VM("[Task-StartVm]"),
        // 删除虚拟机
        TASK_DELETE_VM("[Task-DeleteVm]"),
        // 更新虚拟机系统
        VMHOST_UPDATE_OS("[Vmhost-UpdateOs]"),
        // 系统日志
        SYSTEM("[System]"),
        ;

        private final String type;

        LogType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }
    }

    public static void log(LogType type, String message, Object... args) {
        String formattedMessage = String.format("%s %s", type.getType(), message);
        log.info(formattedMessage, args);
    }
    /**
    * @Author: mryunqi
    * @Description: warn级别日志
    * @DateTime: 2023/8/18 15:17
    */
    public static void warn(LogType type, String message, Object... args) {
        String formattedMessage = String.format("%s %s", type.getType(), message);
        log.warn(formattedMessage, args);
    }
    /**
    * @Author: mryunqi
    * @Description: error级别日志
    * @DateTime: 2023/8/18 15:17
    */
    public static void error(LogType type, String message, Object... args) {
        String formattedMessage = String.format("%s %s", type.getType(), message);
        log.error(formattedMessage, args);
    }

}
