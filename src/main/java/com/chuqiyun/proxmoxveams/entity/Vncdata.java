package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.models.auth.In;
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
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    
    private Long vncId;
    private Integer hostId;
    private Integer nodeId;
    private Integer port;
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

