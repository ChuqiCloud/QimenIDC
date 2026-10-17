package com.chuqiyun.proxmoxveams.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

@Data
public class VmMigrationParams {
    private Integer hostId;

    /**
     * 单台迁移使用；批量迁移改用 hostIds
     */
    @JsonAlias("hostIds")
    private List<Integer> hostIds;

    private Integer targetNodeId;
    private Integer targetVmid;
    private String targetStorage;
    private String backupDir;
    private Boolean startAfterMigration;
}
