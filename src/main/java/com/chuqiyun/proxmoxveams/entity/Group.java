package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * (Group)表实体类
 *
 * @author mryunqi
 * @since 2023-08-14 18:14:15
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "group")
public class Group extends Model<Group> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    
    private String name;
    private Integer parent;
    
    private Integer realm;


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

