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
    
    private Integer cores;
    
    private Integer memory;
    
    private Integer agent;
    
    private String ide0;
    //cloud-init
    private String ide2;
    
    private String net0;
    
    private String net1;
    private String os;
    private Integer bandwidth;
    private String storage;
    private Integer systemDiskSize;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> dataDisk;
    private String  bridge;
/*    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object>  ipconfig;*/
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> task;
    private String status;
    private Long createTime;
    private Long expirationTime;



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

