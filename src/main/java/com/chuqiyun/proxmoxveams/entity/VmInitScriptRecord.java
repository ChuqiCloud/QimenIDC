package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * @Author: 鏄熺
 * @Description: 虚拟机初始化脚本执行记录
 * @DateTime: 2026/7/3 20:47
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vm_init_script_record")
public class VmInitScriptRecord extends Model<VmInitScriptRecord> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private Integer scriptId;
    private Integer hostId;
    private Integer vmid;
    private Integer nodeId;
    private String triggerType;
    private String status;
    private Integer pid;
    private Integer exitCode;
    private String stdout;
    private String stderr;
    private String error;
    private Integer runCount;
    private Long createTime;
    private Long updateTime;
    private Long startTime;
    private Long finishTime;

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
