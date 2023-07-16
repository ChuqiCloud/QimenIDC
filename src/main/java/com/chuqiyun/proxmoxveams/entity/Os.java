package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * (Os)表实体类
 *
 * @author mryunqi
 * @since 2023-07-08 15:58:22
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "os",autoResultMap = true)
public class Os extends Model<Os> {
    
    private Integer id;
    
    private Integer nodeId;
    
    private String name;
    private String downType;
    private String url;
    private Double schedule;
    private String size;
    private String path;
    /**
     * 0:未下载 1:下载中 2:已下载
     */
    private Integer status;
    private Long createTime;


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

