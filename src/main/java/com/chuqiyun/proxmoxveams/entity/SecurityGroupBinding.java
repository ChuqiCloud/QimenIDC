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
 * @Description: 安全组与虚拟机绑定
 * @DateTime: 2026/7/1 22:15
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "security_group_binding")
public class SecurityGroupBinding extends Model<SecurityGroupBinding> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private Integer groupId;
    private Integer hostId;
    private Integer vmId;
    private Integer nodeId;
    private String networkType;
    private Integer status;
    private Long createTime;
    private Long updateTime;

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
