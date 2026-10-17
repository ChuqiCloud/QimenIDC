package com.chuqiyun.proxmoxveams.dto;

import lombok.Data;

/**
 * @Author: 星禾
 * @Description: 二步验证挑战数据，含有效期与前端展示所需信息
 * @DateTime: 2026/10/17 15:36
 */
@Data
public class MfaChallenge {
    private String method;
    private String ticket;
    /**
     * 票据有效期（毫秒）
     */
    private long expiresIn;
    /**
     * passkey 挑战的 publicKey 选项
     */
    private Object publicKeyOptions;
}
