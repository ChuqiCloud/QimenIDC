package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * (Modelgroup)表实体类
 *
 * @author mryunqi
 * @since 2023-08-20 16:04:31
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "modelgroup",autoResultMap = true)
public class Modelgroup extends Model<Modelgroup> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    
    private Integer cpuModel;
    
    private String smbiosModel;
    private String args;
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

