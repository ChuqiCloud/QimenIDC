package com.chuqiyun.proxmoxveams.entity;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * @author mryunqi
 * @date 2023/6/21
 */
@Data
public class VmParams {
    /**
     * 节点id
     */
    private Integer nodeid;
    /**
     * 虚拟机名称
     */
    private String hostname;
    /**
     * 插槽数
     */
    private Integer sockets;
    /**
     * 核心数
     */
    private Integer cores;
    /**
     * 线程数
     */
    private Integer threads;
    /**
     * 是否启用嵌套虚拟化
     */
    private Boolean nested;
    /**
     * 内存大小
     */
    private Integer memory;
    /**
     * 存储
     */
    private String storage;
    /**
     * 系统盘大小
     */
    private Integer systemDiskSize;
    /**
     * 数据盘
     */
    private HashMap<Object,Object> dataDisk;
    /**
     * 网卡
     */
    private String  bridge;
    /**
     * ipconfig
     */
    private HashMap<String,String> ipConfig;
    private String dns1;
    /**
     * 操作系统
     */
    private String os;
    /**
     * 操作系统类型
     */
    private String osType;
    /**
     * iso镜像
     */
    private String iso;
    /**
     * 网络带宽
     */
    private Integer bandwidth;
    /**
     * 虚拟机登录用户名
     */
    private String username;
    /**
     * 虚拟机登录密码
     */
    private String password;
    private Map<Object, Object> task;
}
