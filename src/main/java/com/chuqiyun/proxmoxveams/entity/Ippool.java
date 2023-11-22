package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * (Ippool)表实体类
 *
 * @author mryunqi
 * @since 2023-07-02 19:08:38
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ippool")
public class Ippool extends Model<Ippool> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    
    private Integer nodeId;
    
    private Integer vmId;
    private Integer poolId;
    
    private String ip;
    
    private String subnetMask;
    private String gateway;
    private String mac;
    private String dns1;
    private String dns2;
    
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

