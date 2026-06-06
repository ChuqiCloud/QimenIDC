package com.chuqiyun.proxmoxveams.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: 星禾
 * @Description: 系统日志条目
 * @DateTime: 2026/6/7 00:16
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemLogEntryDto {
    private String timestamp;
    private String level;
    private String requestId;
    private String threadName;
    private String logger;
    private String fileName;
    private String message;
    private String content;
}
