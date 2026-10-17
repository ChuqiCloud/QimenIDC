package com.chuqiyun.proxmoxveams.entity;

import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.chuqiyun.proxmoxveams.security.EncryptedPasswordTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * (Master)表实体类
 *
 * @author mryunqi
 * @since 2023-06-10 01:18:16
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString
@TableName(value = "master", autoResultMap = true)
public class Master extends Model<Master> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String name;
    private Integer area;
    
    private String host;
    
    private Integer port;
    
    private String username;
    
    /**
     * 节点 PVE 凭据只允许服务内部读取，任何 API 序列化都不得返回。
     * WRITE_ONLY 保留管理接口提交新密码的能力；Fastjson2 注解覆盖手工 JSON 序列化路径。
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @JSONField(serialize = false)
    @ToString.Exclude
    @TableField(typeHandler = EncryptedPasswordTypeHandler.class)
    private String password;
    
    private String realm;
    //0正常1异常2停止
    private Integer status;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @JSONField(serialize = false)
    @ToString.Exclude
    @TableField(typeHandler = EncryptedPasswordTypeHandler.class)
    private String csrfToken;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @JSONField(serialize = false)
    @ToString.Exclude
    @TableField(typeHandler = EncryptedPasswordTypeHandler.class)
    private String ticket;
    private String nodeName;
    private String autoStorage;
    private String backupStorage;
    private Integer controllerStatus;
    private Integer controllerPort;
    private Integer naton;
    private String natbridge;
    private Integer natippool;
    private String nataddr;

    /**
     * 节点公钥（PEM 证书）。用于核对节点自签证书是否被替换、看有效期，
     * 属于公开信息，随节点信息一起返回即可。
     * 列名加 node_ 前缀：MySQL 里 public 是保留字。
     */
    @TableField("node_public_key")
    private String nodePublicKey;
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

