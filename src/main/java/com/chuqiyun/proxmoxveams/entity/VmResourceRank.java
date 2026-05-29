package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * VM resource usage rank cache.
 *
 * @author codex
 * @since 2026-05-29
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "vm_resource_rank")
public class VmResourceRank extends Model<VmResourceRank> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String rankType;
    private Integer rankNo;
    private Integer hostId;
    private Integer vmid;
    private String hostname;
    private Integer nodeId;
    private String nodeName;
    private Double cpu;
    private Double cpuPercent;
    private Long memory;
    private Double memoryMb;
    private Long maxMemory;
    private Double maxMemoryMb;
    private Double memoryPercent;
    private Long collectTime;

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
