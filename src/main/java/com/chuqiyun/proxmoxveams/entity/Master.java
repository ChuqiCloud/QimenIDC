package com.chuqiyun.proxmoxveams.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * (Master)表实体类
 *
 * @author mryunqi
 * @since 2023-06-10 01:18:16
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "master")
public class Master extends Model<Master> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String name;
    private Integer area;
    
    private String host;
    
    private Integer port;
    
    private String username;
    
    private String password;
    
    private String realm;
    //0正常1异常2停止
    private Integer status;
    private String csrfToken;
    private String ticket;
    private String nodeName;
    private String autoStorage;
    private Integer sshPort;
    private String sshUsername;
    private String sshPassword;
    private Integer controllerStatus;
    private Integer controllerPort;
    private Integer naton;
    private String natbridge;
    private Integer natippool;
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

