package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * (Vncnode)表实体类
 *
 * @author mryunqi
 * @since 2023-11-22 13:42:25
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "vncnode")
public class Vncnode extends Model<Vncnode> {
    
    private Long id;
    //别称
    private String name;
    
    private String host;
    //控制器端口
    private Integer port;
    
    private String domain;
    //0=true；1=false
    private Integer status;
    //创建日期
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

