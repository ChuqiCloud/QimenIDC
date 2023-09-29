package com.chuqiyun.proxmoxveams.dto;

import lombok.Data;

/**
 * @author mryunqi
 * @date 2023/9/29
 */
@Data
public class RenewalParams {
    /**
     * 虚拟机id
     */
    private Integer hostId;
    /**
     * 到期时间
     */
    private Long expirationTime;
}
