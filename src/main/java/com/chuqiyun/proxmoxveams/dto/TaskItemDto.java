package com.chuqiyun.proxmoxveams.dto;

import com.chuqiyun.proxmoxveams.security.TaskPasswordValues;
import lombok.Data;

import java.util.Map;

/**
 * 任务列表/详情返回给管理员的任务信息。
 *
 * <p>参数中的密码、token 等凭据在实体层已被删除，这里再做一次防御性脱敏，
 * 保证任何路径下都不会把凭据带出接口。</p>
 *
 * @author CodeBuddy
 * @date 2026/10/17
 */
@Data
public class TaskItemDto {
    private Integer id;
    private Integer nodeid;
    private String nodeName;
    private Integer hostid;
    private Integer vmid;
    private String hostname;
    private Integer type;
    private String typeName;
    private Integer status;
    private String statusName;
    /** 进度百分比，任务未上报进度时为 null */
    private Integer progress;
    private String stage;
    private String error;
    private Long createDate;
    private Long updateTime;
    /** 已耗时/总耗时（毫秒），任务未结束且未记录 updateTime 时为 null */
    private Long duration;
    private Map<Object, Object> params;

    public void setParams(Map<Object, Object> params) {
        this.params = TaskPasswordValues.sanitizeForResponse(params);
    }
}
