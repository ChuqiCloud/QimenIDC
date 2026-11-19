package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/** 持久化虚拟机救援系统的挂载与恢复状态。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rescue_vm_state")
public class RescueVmState extends Model<RescueVmState> {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Integer hostId;
    private Integer nodeId;
    private Integer vmid;
    private String imageId;
    private String imageUrl;
    private String fileName;
    private String storage;
    private String originalIde2;
    private String originalBoot;
    private Integer originalRunning;
    private String isoVolume;
    private String status;
    private String error;
    private Long createTime;
    private Long updateTime;

    @Override
    public Serializable pkVal() {
        return id;
    }
}
