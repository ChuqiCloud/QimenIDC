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
 * @Description: 系统日志实体
 * @DateTime: 2026/6/7 11:12
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "system_log")
public class SystemLog extends Model<SystemLog> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String requestId;

    private String logType;

    private String level;

    private String method;

    private String uri;

    private String pathPattern;

    private String handler;

    private String clientIp;

    private String operator;

    private String authType;

    private String queryString;

    private String requestBody;

    private Integer httpStatus;

    private Integer businessCode;

    private String businessMessage;

    private String responseBody;

    private Long durationMs;

    private String exception;

    private String content;

    private Long createTime;

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
