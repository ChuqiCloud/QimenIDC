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
     * 获取主键值
     *
     * @return 主键值
     */
    @Override
    public Serializable pkVal() {
        return this.id;
    }

}

