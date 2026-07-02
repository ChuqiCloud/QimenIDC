package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * @Author: 星禾
 * @Description: 安全组规则
 * @DateTime: 2026/7/1 22:15
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "security_group_rule")
public class SecurityGroupRule extends Model<SecurityGroupRule> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    @TableField(exist = false)
    private Integer hostId;
    private Integer groupId;
    private String direction;
    private String protocol;
    private Integer portStart;
    private Integer portEnd;
    private String remoteCidr;
    private Integer remoteGroupId;
    private String action;
    private Integer priority;
    private String remark;
    private Integer status;
    private Long createTime;
    private Long updateTime;

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
