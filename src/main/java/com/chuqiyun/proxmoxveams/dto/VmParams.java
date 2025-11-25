package com.chuqiyun.proxmoxveams.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
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
     * 配置模板ID
     */
    private Integer configureTemplateId;
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
     * cpu模型
     */
    private Integer cpuModel;
    /**
     * 模型组合，modelGroup优先级高于cpuModel
     */
    private Integer modelGroup;
    /**
     * cpu类型
     */
    private String cpu;
    /**
     * cpu限制(单位:百分比)
     */
    private Integer cpuUnits;
    /**
     * I/O限制(单位:KB/s)
     */
    private Long bwlimit;
    /**
     * 月流量限制(单位:GB)
     */
    private Long flowLimit;
    /**
     * 月流量使用量(单位:GB)
     */
    private Double usedFlow;
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
     * 系统磁盘读取长效限制 单位mb/s
     */
    private Integer mbpsRd;
    /**
     * 系统磁盘读取突发限制 单位mb/s
     */
    private Integer mbpsRdMax;
    /**
     * 系统磁盘写入长效限制 单位mb/s
     */
    private Integer mbpsWr;
    /**
     * 系统磁盘写入突发限制 单位mb/s
     */
    private Integer mbpsWrMax;
    /**
     * 系统磁盘iops读取长效限制 单位ops/s
     */
    private Integer iopsRd;
    /**
     * 系统磁盘iops读取突发限制 单位ops/s
     */
    private Integer iopsRdMax;
    /**
     * 系统磁盘iops写入长效限制 单位ops/s
     */
    private Integer iopsWr;
    /**
     * 系统磁盘iops写入突发限制 单位ops/s
     */
    private Integer iopsWrMax;
    /**
     * 数据盘
     */
    private HashMap<Object,Object> dataDisk;
    /**
     * 网卡
     */
    private String bridge;
    /**
     * ipconfig
     */
    private HashMap<String,String> ipConfig;
    private List<IpDto> ipData;
    private String dns1;
    /**
     * 操作系统
     */
    private String os;
    private String osName;
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
    // 临时ip list
    private List<String> ipList;
    // 是否开启Nat 0关闭 1开启
    private Integer ifnat;
    // nat数量
    private Integer natnum;
    // 额外流量包
    private Long extraFlowLimit;
    private Integer resetFlowTime;
}
