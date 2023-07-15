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
 * (Task)表实体类
 *
 * @author mryunqi
 * @since 2023-06-29 22:38:30
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "task",autoResultMap = true)
public class Task extends Model<Task> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    
    private Integer nodeid;
    
    private Integer vmid;
    private Integer hostid;
    
    private Integer type;
    
    private Integer status;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Object, Object> params;
    private String error;
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

