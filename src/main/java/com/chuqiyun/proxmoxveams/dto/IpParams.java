package com.chuqiyun.proxmoxveams.dto;

import lombok.Data;

/**
 * @author mryunqi
 * @date 2023/7/2
 */
@Data
public class IpParams {
    // 池名
    private String poolName;
    // IP池ID
    private Integer poolId;
    // subnet id
    private Integer subnetId;
    // 起始ip
    private String startIp;
    // 结束ip
    private String endIp;
    // 网关
    private String gateway;
    // 掩码位
    private Integer mask;
    // 节点id
    private Integer nodeId;
    // dns1
    private String dns1;
    // dns2
    private String dns2;
}
