package com.chuqiyun.proxmoxveams.dto;

import lombok.Data;

@Data
public class SystemDiskConfig {
    /**
     * 磁盘类型 scsi, ide, virtio, sata
     */
    private String type;

}
