package com.chuqiyun.proxmoxveams.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class VmMigrationLogView {
    private Integer taskId;
    private String stage;
    private String stageText;
    private Integer progress;
    private String status;
    private List<String> logs = new ArrayList<>();
    /**
     * 下次增量拉取的游标，前端原样带回即可
     */
    private Long nextOffset;
}
