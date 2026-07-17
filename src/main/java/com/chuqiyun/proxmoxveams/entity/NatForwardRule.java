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
 * @Description: 主控NAT转发规则
 * @DateTime: 2026/7/17 06:06
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "nat_forward_rule")
public class NatForwardRule extends Model<NatForwardRule> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String ruleKey;
    private String ruleType;
    private Integer nodeId;
    private Integer hostId;
    private String sourceIp;
    private Integer sourcePort;
    private String destinationIp;
    private Integer destinationPort;
    private String protocol;
    private Long createTime;
    private Long updateTime;

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
