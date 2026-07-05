package com.chuqiyun.proxmoxveams.dto;

import lombok.Data;

@Data
public class VmMigrationParams {
    private Integer hostId;
    private Integer targetNodeId;
    private Integer targetVmid;
    private String targetStorage;
    private String backupDir;
    private Boolean startAfterMigration;
}
