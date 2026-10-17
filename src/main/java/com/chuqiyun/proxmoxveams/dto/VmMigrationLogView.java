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
     * 下次增量拉取的不透明游标，前端必须原样带回，不要按行数或字节数解析。
     */
    private String nextOffset;
}
