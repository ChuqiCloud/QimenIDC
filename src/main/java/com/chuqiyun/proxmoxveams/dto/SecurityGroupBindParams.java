package com.chuqiyun.proxmoxveams.dto;

import lombok.Data;

import java.util.List;

/**
 * @Author: 星禾
 * @Description: 安全组绑定参数
 * @DateTime: 2026/7/1 22:20
 */
@Data
public class SecurityGroupBindParams {
    private Integer hostId;
    private Integer groupId;
    private List<Integer> groupIds;
}
