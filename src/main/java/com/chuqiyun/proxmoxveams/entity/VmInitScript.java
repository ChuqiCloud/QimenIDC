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
 * @Description: 虚拟机初始化脚本模板
 * @DateTime: 2026/7/3 20:47
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vm_init_script")
public class VmInitScript extends Model<VmInitScript> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String name;
    private String scriptType;
    private String runMode;
    private String targetOs;
    private String content;
    private String linuxContent;
    private String windowsContent;
    private Integer timeoutSeconds;
    private Integer status;
    private String remark;
    private Long createTime;
    private Long updateTime;

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
