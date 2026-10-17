package com.chuqiyun.proxmoxveams.entity;

import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * (Sysuser)表实体类
 *
 * @author mryunqi
 * @since 2023-06-10 15:31:52
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sysuser")
public class Sysuser extends Model<Sysuser> {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String uuid;
    
    private String username;
    
    @JSONField(serialize = false)
    private String password;
    private String name;
    
    private String phone;
    
    private String email;
    //上次登录时间
    private Long logindate;

    /**
     * 二步验证方式：none / totp / passkey
     */
    private String mfaType;

    /**
     * base64url 编码的 TOTP 密钥，仅服务端使用；密钥本身用数据库凭据方式加密存储
     */
    @JSONField(serialize = false)
    private String totpSecret;

    /**
     * TOTP 绑定的确认时间，为空代表尚未绑定完成
     */
    private Long totpConfirmedTime;

    /**
     * 已消费的最新 TOTP 时间片，用于阻止同一动态码重复使用
     */
    @JSONField(serialize = false)
    private Long totpLastStep;

    /**
     * 是否已绑定至少一个 Passkey
     */
    private Integer passkeyEnabled;

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

