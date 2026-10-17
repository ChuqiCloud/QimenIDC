package com.chuqiyun.proxmoxveams.dto;

import lombok.Data;

import java.util.List;

/**
 * 任务查询返回结果，含分页信息与脱敏后的任务列表。
 *
 * @author CodeBuddy
 * @date 2026/10/17
 */
@Data
public class TaskQueryDto {
    // 以下字段既是查询条件，也是返回的分页信息
    private Integer nodeid;
    private Integer hostid;
    private Integer vmid;
    /** 任务类型，支持逗号分隔 */
    private String type;
    /** 任务状态，支持逗号分隔 */
    private String status;
    private Long startDate;
    private Long endDate;

    private Long total;
    private Long page;
    private Long size;
    private Long pages;
    private List<TaskItemDto> records;
}
