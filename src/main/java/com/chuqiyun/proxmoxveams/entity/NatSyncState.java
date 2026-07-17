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
 * @Description: NAT规则节点同步状态
 * @DateTime: 2026/7/17 06:06
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "nat_sync_state")
public class NatSyncState extends Model<NatSyncState> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private Integer nodeId;
    private Integer initialImported;
    private Integer inSync;
    private String lastMessage;
    private Long lastImportTime;
    private Long lastSyncTime;

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
