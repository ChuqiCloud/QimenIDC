package com.chuqiyun.proxmoxveams.service;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.dto.LoginResult;
import com.chuqiyun.proxmoxveams.dto.MfaChallenge;
import com.chuqiyun.proxmoxveams.entity.Sysuser;

/**
 * @Author: 星禾
 * @Description: 登录流程与二步验证编排
 * @DateTime: 2026/10/17 15:40
 */
public interface AuthService {

    /**
     * 第一步：账号密码校验，需要二步验证时返回挑战票据
     */
    LoginResult loginWithPassword(String username, String password) throws UnauthorizedException;

    /**
     * 第二步：用 TOTP 完成登录
     */
    String loginWithTotp(String ticket, String code) throws UnauthorizedException;

    /**
     * 第二步（Passkey）：生成认证挑战
     */
    MfaChallenge passkeyLoginChallenge(String ticket) throws UnauthorizedException;

    /**
     * 第二步（Passkey）：校验断言结果并签发 token
     */
    String loginWithPasskey(String ticket, JSONObject credential) throws UnauthorizedException;

    /**
     * 一步登录：Passkey 直接作为主认证方式，无需二步验证
     */
    MfaChallenge passkeyDirectChallenge(String username) throws UnauthorizedException;

    /**
     * 一步登录（Passkey）：校验断言结果，若账号还启用了 TOTP 则继续要求二步验证
     */
    LoginResult loginWithPasskeyDirect(String username, JSONObject credential) throws UnauthorizedException;

    /**
     * 记录登录成功时间并签发 token
     */
    String issueLoginToken(Sysuser sysuser);
}
