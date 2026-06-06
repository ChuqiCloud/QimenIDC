package com.chuqiyun.proxmoxveams.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

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
     * 节点ID，批量同步节点下虚拟机手动绑定IP时使用
     */
    @JsonAlias("nodeid")
    private Integer nodeId;
    /**
     * 新IP地址，和newIp二选一；为空时自动从空闲IP中分配
     */
    private String ip;
    /**
     * 新IP地址，兼容前端字段命名
     */
    private String newIp;
    /**
     * 批量新增IP地址列表；为空时根据count自动从IP池分配
     */
    private List<String> ips;
    /**
     * 新增IP数量；未传ips时生效，默认新增1个
     */
    private Integer count;
    /**
     * 指定IP池ID；新增指定ips时用于限定IP池
     */
    private Integer poolId;
    /**
     * cloud-init IP序号，从1开始；默认修改第1个IP，即PVE的ipconfig0
     */
    private Integer networkIndex;
}
