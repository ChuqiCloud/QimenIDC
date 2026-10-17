package com.chuqiyun.proxmoxveams.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务按类型或状态的计数。
 *
 * @author CodeBuddy
 * @date 2026/10/17
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskCountDto {
    private Integer type;
    private String typeName;
    private Integer status;
    private String statusName;
    private Long count;
}
