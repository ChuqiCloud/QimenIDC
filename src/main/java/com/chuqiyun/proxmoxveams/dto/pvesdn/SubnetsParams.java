package com.chuqiyun.proxmoxveams.dto.pvesdn;

import lombok.Data;

/**
 * @author mryunqi
 * @date 2024/1/20
 */
@Data
public class SubnetsParams {
    /**
     * 	SDN子网对象标识符
     */
    private String subnet;
    /**
     * 	type 恒定值“subnet”
     */
    private String type;
    /**
     * 	gateway 网关地址
     */
    private String gateway;
    /**
     * 	snat 使子网启用源网络地址转换
     */
    private Boolean snat;
    /**
     * 	vnet vnet标识符
     */
    private String vnet;
}
