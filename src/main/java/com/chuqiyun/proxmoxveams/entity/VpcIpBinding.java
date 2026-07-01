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
 * @Description: VPC公网IP与私网IP绑定关系
 * @DateTime: 2026/7/1 21:00
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "vpc_ip_binding")
public class VpcIpBinding extends Model<VpcIpBinding> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Integer hostId;

    private Integer vmId;

    private Integer nodeId;

    private Integer vpcSubnetId;

    private Integer ippoolId;

    private Integer subnetpoolId;

    private String publicIp;

    private String privateIp;

    private String forwardMode;

    private Integer status;

    private Long createTime;

    private Long updateTime;

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
