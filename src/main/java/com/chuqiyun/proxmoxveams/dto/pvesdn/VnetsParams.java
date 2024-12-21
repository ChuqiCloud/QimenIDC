package com.chuqiyun.proxmoxveams.dto.pvesdn;

import lombok.Data;

/**
 * @author mryunqi
 * @date 2024/1/20
 */
@Data
public class VnetsParams {
    /**
     * 	vnet 标识符
     */
    private String vnet;
    /**
     * 	zone sdn区域标识符
     */
    private String zone;
    /**
     * 	alias 别称 (?^i:[\(\)-_.\w\d\s]{0,256})
     */
    private String alias;
    /**
     * 	tag vlan或id
     */
    private Integer tag;
    /**
     * 	type 默认vnet
     */
    private String type;
    /**
     * 	vlanaware 使vnet vlan感知
     */
    private Boolean vlanaware;
}
