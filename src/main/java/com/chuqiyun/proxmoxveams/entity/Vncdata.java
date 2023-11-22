package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * (Vncdata)表实体类
 *
 * @author mryunqi
 * @since 2023-11-22 13:43:08
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "vncdata")
public class Vncdata extends Model<Vncdata> {
    
    private Long id;
    
    private Long vncId;
    //0=正常；1=失效
    private Integer status;
    
    private Long createDate;
    //失效时间
    private Long expirationTime;


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

