package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * (Zones)表实体类
 *
 * @author mryunqi
 * @since 2024-01-20 17:48:40
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "zones",autoResultMap = true)
public class Zones extends Model<Zones> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private  Integer nodeId;
    
    private String type;
    
    private String zone;
    
    private String nodes;
    
    private String ipam;
    
    private String dns;
    
    private String reversedns;
    
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

