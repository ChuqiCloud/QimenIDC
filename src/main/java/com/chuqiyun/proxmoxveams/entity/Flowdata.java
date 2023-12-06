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
 * (Flowdata)表实体类
 *
 * @author mryunqi
 * @since 2023-12-03 20:21:54
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "flowdata",autoResultMap = true)
public class Flowdata extends Model<Flowdata> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    
    private Integer nodeId;
    
    private Integer hostid;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String,String> rrd;
    //已用流量
    private Double usedFlow;
    //0=未同步;1=已同步
    private Integer status;
    
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

