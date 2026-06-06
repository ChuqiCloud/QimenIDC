package com.chuqiyun.proxmoxveams.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author: 星禾
 * @Description: 系统日志查询结果
 * @DateTime: 2026/6/7 00:16
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemLogQueryResultDto {
    private String type;
    private String keyword;
    private String date;
    private Integer lines;
    private Integer total;
    private List<SystemLogEntryDto> data;
}
