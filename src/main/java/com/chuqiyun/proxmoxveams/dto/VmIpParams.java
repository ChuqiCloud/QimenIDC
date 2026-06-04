package com.chuqiyun.proxmoxveams.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

/**
 * @Author: 星禾
 * @Description: 虚拟机IP修改参数
 * @DateTime: 2026/6/4 20:14
 */
@Data
public class VmIpParams {
    /**
     * 数据库中虚拟机ID
     */
    @JsonAlias("hostid")
    private Integer hostId;
    /**
     * 新IP地址，和newIp二选一；为空时自动从空闲IP中分配
     */
    private String ip;
    /**
     * 新IP地址，兼容前端字段命名
     */
    private String newIp;
    /**
     * 指定IP池ID；为空时优先沿用原IP所在IP池
     */
    private Integer poolId;
    /**
     * cloud-init IP序号，从1开始；默认修改第1个IP，即PVE的ipconfig0
     */
    private Integer networkIndex;
}
