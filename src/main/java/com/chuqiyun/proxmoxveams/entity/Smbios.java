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
 * (Smbios)表实体类
 *
 * @author mryunqi
 * @since 2023-08-19 13:00:19
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "smbios",autoResultMap = true)
public class Smbios extends Model<Smbios> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    
    private Integer type;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> model;
    
    private String info;
    
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

