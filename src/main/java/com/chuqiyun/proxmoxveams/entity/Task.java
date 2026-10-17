package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.alibaba.fastjson2.annotation.JSONField;
import com.chuqiyun.proxmoxveams.security.EncryptedTaskParamsTypeHandler;
import com.chuqiyun.proxmoxveams.security.TaskPasswordValues;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;
import java.util.Map;

/**
 * (Task)表实体类
 *
 * @author mryunqi
 * @since 2023-06-29 22:38:30
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString
@TableName(value = "task",autoResultMap = true)
public class Task extends Model<Task> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    
    private Integer nodeid;
    
    private Integer vmid;
    private Integer hostid;
    
    private Integer type;
    //private Integer masterType;
    
    private Integer status;
    /** 任务执行参数可能包含新密码等凭据，仅允许服务内部读取。 */
    @JsonIgnore
    @JSONField(serialize = false)
    @ToString.Exclude
    @TableField(typeHandler = EncryptedTaskParamsTypeHandler.class)
    private Map<Object, Object> params;
    //private Integer masterId;
    private String error;
    private Long createDate;

    /** 任务执行器内部使用，不向接口输出原始参数。 */
    @JsonIgnore
    @JSONField(serialize = false)
    public Map<Object, Object> getParams() {
        return params;
    }

    /**
     * 对外保留任务进度等普通参数，密码、票据和 token 字段由统一脱敏器删除。
     */
    @JsonProperty("params")
    @JSONField(name = "params")
    public Map<Object, Object> getParamsForApi() {
        return TaskPasswordValues.sanitizeForResponse(params);
    }


    /**
     * 获取主键值
     *
     * @return 主键值
     */
    @Override
    public Serializable pkVal() {
        return this.id;
    }
    }

