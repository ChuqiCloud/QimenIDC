package com.chuqiyun.proxmoxveams.dto;

import lombok.Data;

/**
 * @Author: 星禾
 * @Description: 登录结果，token 为空表示还需要完成二步验证
 * @DateTime: 2026/10/17 15:35
 */
@Data
public class LoginResult {
    /**
     * 是否已经完成全部登录步骤
     */
    private boolean completed;

    /**
     * 正式登录 token
     */
    private String token;

    /**
     * 二步验证方式 none/totp/passkey
     */
    private String method;

    /**
     * 二步验证挑战票据
     */
    private String ticket;

    public static LoginResult done(String token) {
        LoginResult result = new LoginResult();
        result.setCompleted(true);
        result.setToken(token);
        return result;
    }

    public static LoginResult mfaRequired(String method, String ticket) {
        LoginResult result = new LoginResult();
        result.setCompleted(false);
        result.setMethod(method);
        result.setTicket(ticket);
        return result;
    }
}
