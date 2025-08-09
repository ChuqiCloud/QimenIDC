package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * (Ipstatus)表实体类
 *
 * @author mryunqi
 * @since 2023-07-02 23:16:38
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ipstatus")
public class Ipstatus extends Model<Ipstatus> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    
    private String name;
    private Integer ipType;
    
    private String gateway;
    
    private Integer mask;
    
    private String dns1;
    
    private String dns2;
    //可用
    private Integer available;
    //已用
    private Integer used;
    //禁用
    private Integer disable;
    
    private Integer nodeid;


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

