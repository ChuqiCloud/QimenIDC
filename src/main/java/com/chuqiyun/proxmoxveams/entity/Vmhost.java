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
    private Integer nodeid;
    
    private Integer vmid;
    
    private String name;
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
    
    private Integer memory;
    /**
     * swap大小
     */
    private Integer swap;
    
    private Integer agent;
    private String username;
    private String password;
    
    private String ide0;
    //cloud-init
    private String ide2;
    
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
    private Long createTime;
    private Long expirationTime;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> ipList;



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

