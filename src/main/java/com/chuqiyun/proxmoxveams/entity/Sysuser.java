package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * (Sysuser)表实体类
 *
 * @author mryunqi
 * @since 2023-06-10 15:31:52
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sysuser")
public class Sysuser extends Model<Sysuser> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    
    private String username;
    
    private String password;
    private String name;
    
    private String phone;
    
    private String email;
    //上次登录时间
    private Long logindate;

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

