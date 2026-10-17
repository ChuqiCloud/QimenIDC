package com.chuqiyun.proxmoxveams.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 迁移任务视图。前端刷新后按 hostId 或 batchId 重新拉取，不必持有上一次请求里的任务ID。
 */
@Data
public class VmMigrationTaskView {
    private Integer taskId;
    private Integer hostId;
    private String hostName;
    private String batchId;
    private Integer sourceNodeId;
    private Integer targetNodeId;
    private String targetNodeName;
    private Integer sourceVmid;
    private Integer targetVmid;
    private String targetStorage;
    private Integer status;
    private String stage;
    private String stageText;
    private Integer progress;
    private Integer retryCount;
    private String lastFailedStep;
    private String error;
    private Boolean resumable;
    private Long createDate;
    private Long updateTime;
    private Integer queuePosition;

    /**
     * 过程日志由被控端日志与主控阶段日志拼接而成
     */
    private List<String> logs;

    @JsonProperty("params")
    private Map<Object, Object> params = new HashMap<>();
}
