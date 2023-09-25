package com.chuqiyun.proxmoxveams.dto;

import lombok.Data;

/**
 * @author mryunqi
 * @date 2023/9/24
 */
@Data
public class IpDto {
    private String ip;
    private Integer subnetMask;
    /*网关*/
    private String gateway;
}
