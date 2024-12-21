package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * (Subnet)表实体类
 *
 * @author mryunqi
 * @since 2024-01-20 17:47:20
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "subnet",autoResultMap = true)
public class Subnet extends Model<Subnet> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private Integer nodeid;
    
    private String subnet;
    
    private String type;
    
    private String vnet;
    
    private String gateway;
    private Integer mask;
    
    private String dns;
    
    private Integer snat;
    //可用
    private Integer available;
    //已用
    private Integer used;
    //禁用
    private Integer disable;
    private String state;


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

