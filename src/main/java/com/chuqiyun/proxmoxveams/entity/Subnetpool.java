package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.models.auth.In;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * (Subnetpool)表实体类
 *
 * @author mryunqi
 * @since 2024-01-26 13:53:07
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "subnetpool",autoResultMap = true)
public class Subnetpool extends Model<Subnetpool> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    
    private Integer nodeId;
    
    private Integer vmId;
    
    private Integer subnatId;
    
    private String ip;
    
    private Integer mask;
    
    private String gateway;
    
    private String mac;
    
    private String dns;
    
    private Integer status;


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

