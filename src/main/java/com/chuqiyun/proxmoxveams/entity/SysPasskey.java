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
 * 管理员绑定的 Passkey（WebAuthn 凭据）
 *
 * @author 星禾
 * @since 2026-10-17
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_passkey")
public class SysPasskey extends Model<SysPasskey> {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Integer userid;

    /**
     * base64url 编码的凭据 ID，也是 WebAuthn 侧的唯一标识
     */
    private String credentialId;

    /**
     * base64url 编码的 COSE 公钥
     */
    private String publicKey;

    private Long signCount;

    /**
     * 备份可用（可跨设备同步）
     */
    private Integer backupEligible;

    private Integer backupState;

    /**
     * 认证器使用的算法，目前仅支持 ES256(-7) 与 RS256(-257)
     */
    private Integer algorithm;

    /**
     * 展示名称，例如 "Chrome on macOS"
     */
    private String deviceName;

    /**
     * base64url 编码的 AAGUID
     */
    private String aaguid;

    private Long createTime;

    private Long lastUsedTime;

    @Override
    @JSONField(serialize = false)
    public Serializable pkVal() {
        return this.id;
    }
}
