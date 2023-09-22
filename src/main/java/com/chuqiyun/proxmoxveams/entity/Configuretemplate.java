package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 配置模板(Configuretemplate)表实体类
 *
 * @author mryunqi
 * @since 2023-09-21 22:10:13
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "configuretemplate",autoResultMap = true)
public class Configuretemplate extends Model<Configuretemplate> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    
    private String name;
    //核数
    private Integer cores;
    
    private Integer sockets;
    
    private Integer threads;

    private Integer devirtualization;
    
    private Integer kvm;
    
    private Integer cpuModel;
    
    private Integer modelGroup;
    
    private Integer nested;
    
    private String cpu;
    
    private Integer cpuUnits;
    
    private String arch;
    
    private Integer acpi;
    
    private Integer memory;
    
    private String storage;
    
    private Integer systemDiskSize;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> dataDisk;
    
    private Integer bandwidth;
    
    private Integer onboot;

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

