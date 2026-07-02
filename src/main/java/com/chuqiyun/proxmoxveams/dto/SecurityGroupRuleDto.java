package com.chuqiyun.proxmoxveams.dto;

import lombok.Data;

import java.util.List;

/**
 * @Author: 星禾
 * @Description: 安全组有效规则
 * @DateTime: 2026/7/1 22:20
 */
@Data
public class SecurityGroupRuleDto {
    private String direction;
    private String protocol;
    private Integer portStart;
    private Integer portEnd;
    private String remoteCidr;
    private String action;
    private Integer priority;
    private List<String> remoteIps;
}
