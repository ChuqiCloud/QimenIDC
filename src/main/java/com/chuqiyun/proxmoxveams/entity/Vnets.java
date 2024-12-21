package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * (Vnets)表实体类
 *
 * @author mryunqi
 * @since 2024-01-20 17:47:41
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "vnets",autoResultMap = true)
public class Vnets extends Model<Vnets> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    
    private String vnet;
    
    private String zone;
    
    private String alias;
    
    private Integer tag;
    
    private String type;
    
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

