package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.chuqiyun.proxmoxveams.dto.IpDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * (Vmhost)表实体类
 *
 * @author mryunqi
 * @since 2023-06-21 15:11:18
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "vmhost",autoResultMap = true)
public class Vmhost extends Model<Vmhost> {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String uuid;
    private Integer nodeid;
    
    private Integer vmid;
    
    private String hostname;

    /**
     * 虚拟机类型 0:pve 1:hyperv
     */
    private  Integer type;
    /**
     * 配置模板ID
     */
    private Integer configureTemplateId;
    /**
     * 插槽数
     */
    private Integer sockets;
    
    private Integer cores;
    /**
     * 线程数
     */
    private Integer threads;
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
     * 上次重置流量时间
     */
    private Long lastResetFlow;
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
     * 虚拟机亲和性
     */
    private String affinity;
    /**
     * scsihw 控制器类型 默认virtio-scsi-pci
     */
    private String scsihw;
    
    private Integer memory;
    /**
     * swap大小
     */
    private Integer swap;
    
    private Integer agent;
    /**
     * amdSev
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> amdSev;
    private String archive;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> audio0;
    private Integer autostart;
    private Integer balloon;
    private String bios;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> boot;
    private String cdrom;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> cicustom;
    private Integer ciupgrade;
    private Integer cpulimit;
    private String description;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> efidisk0;
    //private Integer force;
    private Integer freeze;
    private String hookscript;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> hostpci;
    private String hotplug;
    private String hugepages;
    private String username;
    private String password;
    
    private String ide0;
    //cloud-init
    private String ide2;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> ide3;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> net;

    private String net0;
    
    private String net1;
    private String os;
    /**
     * 创建时传递的参数
     */
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
     * ivshmem
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> ivshmem;

    private Integer keephugepages;
    /**
     * 键盘布局
     */
    private String keyboard;
    private Integer liveRestore;
    //private Integer localtime;
   // private String lock;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> machine;
    private Float migrateDowntime;
    private Integer migrateSpeed;
    private String nameserver;
    private Integer numa;
    private String parallel;
    private String pool;
    private Integer protection;
    private Integer reboot;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> rng0;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> sata;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> scsi;
    private String  searchdomain;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> serial;
    private Integer shares;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> smbios1;
    private Integer smp;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> spiceEnhancements;
    private String sshkeys;
    private Integer start;
    private String startdate;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> startup;
    private Integer tablet;
    private String tags;
    private Integer tdf;
    private Integer template0;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> tpmstate0;
    //private Integer unique;
    private String unused;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> usb;
    private Integer vcpus;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> vga;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> virtio;
    private String vmgenid;
    private String vmstatestorage;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> watchdog;
    /**
     * 虚拟机模板
     */
    private String template;
    /**
     * 是否开机自启 0:否 1:是
     */
    private Integer onBoot;
    private Integer bandwidth;
    private String storage;
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
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> dataDisk;
    private String  bridge;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String>  ipConfig;
    /**
     * ip数据(为ipConfig的拆解完善版)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<IpDto> ipData;
    private Integer nested;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> task;
    private Integer status;
    /**
     * 暂停原因
     */
    private String pauseInfo;
    private Long createTime;
    private Long expirationTime;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> ipList;
    private Integer ifnat;
    private Integer natnum;




    /**
     * 获取主键值
     *
     * @return 主键值
     */
    @Override
    public Serializable pkVal() {
        return this.id;
    }
    }

