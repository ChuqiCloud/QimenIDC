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
        // 需要时添加更多日志类型
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
}
