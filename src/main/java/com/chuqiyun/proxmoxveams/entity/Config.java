package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * (Config)表实体类
 *
 * @author mryunqi
 * @since 2023-07-11 17:58:04
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "config")
public class Config extends Model<Config> {
    
    private Integer id;
    
    private String token;
    // 全局Linux系统盘大小
    private Integer linuxSystemDiskSize;
    // 全局Windows系统盘大小
    private Integer winSystemDiskSize;
    /**
     * I/O限制(单位:KB/s)
     */
    private Long bwlimit;
    /**
     * vnc过期时间(单位:分钟)
     */
    private Integer vncTime;
    /**
     * 正式版号
     */
    private String version;
    /**
     * 内部构建号
     */
    private String build;
    /**
     * 是否已导入数据库
     */
    private Integer installed;
    /**
     * 最大同时导入磁盘数量
     */
    private Integer importDiskMax;

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

