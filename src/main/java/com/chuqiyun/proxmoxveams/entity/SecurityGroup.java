package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * @Author: 星禾
 * @Description: 安全组
 * @DateTime: 2026/7/1 22:15
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "security_group")
public class SecurityGroup extends Model<SecurityGroup> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private Integer hostId;
    private String name;
    private String description;
    private String defaultIngressAction;
    private String defaultEgressAction;
    private Integer isTemplate;
    private Integer isDefault;
    private Integer status;
    private Long createTime;
    private Long updateTime;

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
