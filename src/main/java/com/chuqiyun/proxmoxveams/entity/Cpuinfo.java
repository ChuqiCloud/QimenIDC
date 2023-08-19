package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * (Cpuinfo)表实体类
 *
 * @author mryunqi
 * @since 2023-08-19 12:59:59
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cpuinfo",autoResultMap = true)
public class Cpuinfo extends Model<Cpuinfo> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    
    private String cpu;
    
    private String name;
    
    private Integer family;
    
    private Integer model;
    
    private Integer stepping;
    private String level;
    private String xlevel;
    private String vendor;
    private Boolean l3Cache;
    
    private Long createDate;


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

