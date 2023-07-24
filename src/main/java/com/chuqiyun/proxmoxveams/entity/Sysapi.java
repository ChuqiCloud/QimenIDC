package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * (Sysapi)表实体类
 *
 * @author mryunqi
 * @since 2023-06-10 19:11:15
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sysapi")
public class Sysapi extends Model<Sysapi> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    
    private String appid;
    
    private String appkey;
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

