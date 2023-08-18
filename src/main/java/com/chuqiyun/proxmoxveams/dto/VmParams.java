package com.chuqiyun.proxmoxveams.dto;

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
     * 数据库中虚拟机ID
     */
    private Integer hostid;
    /**
     * 虚拟机id
     */
    private Integer vmid;
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
     * 是否去虚拟化
     */
    private Boolean devirtualization;
    /**
    * 是否启用kvm
    */
    private Boolean kvm;
    /**
     * cpu类型
     */
    private String cpu;
    /**
     * cpu限制(单位:百分比)
     */
    private Integer cpuUnits;
    /**
     * args 命令集参数
     */
    private String args;
    /**
     * 系统架构(x86_64,arrch64)
     */
    private String arch;
    /**
     * acpi 默认1 开启
     */
    private Integer acpi;
    /**
     * 内存大小
     */
    private Integer memory;
    /**
     * swap大小
     */
    private Integer swap;
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
     * 虚拟机模板
     */
    private String template;
    /**
     * 是否开机自启 0:否 1:是
     */
    private Integer onBoot;
    /**
     * 网络带宽 单位:MB 0:不限制
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
    private String status;
    private Long expirationTime;
}
