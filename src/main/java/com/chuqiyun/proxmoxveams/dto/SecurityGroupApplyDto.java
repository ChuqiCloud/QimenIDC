package com.chuqiyun.proxmoxveams.dto;

import lombok.Data;

import java.util.List;

/**
 * @Author: 星禾
 * @Description: 安全组下发参数
 * @DateTime: 2026/7/1 22:20
 */
@Data
public class SecurityGroupApplyDto {
    private Integer hostId;
    private Integer vmId;
    private String networkType;
    private List<String> targetIps;
    private String defaultIngressAction;
    private String defaultEgressAction;
    private List<SecurityGroupRuleDto> rules;
}
