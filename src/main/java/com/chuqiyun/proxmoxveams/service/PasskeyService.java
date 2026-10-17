package com.chuqiyun.proxmoxveams.service;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.common.AuthException;
import com.chuqiyun.proxmoxveams.entity.SysPasskey;
import com.chuqiyun.proxmoxveams.entity.Sysuser;

import java.util.List;
import java.util.Map;

/**
 * @Author: 星禾
 * @Description: Passkey（WebAuthn）注册与认证
 * @DateTime: 2026/10/17 16:00
 */
public interface PasskeyService {

    /**
     * 生成注册挑战（需已登录）
     */
    Map<String, Object> createRegistrationOptions(Sysuser sysuser) throws AuthException;

    /**
     * 校验注册结果并保存凭据
     */
    SysPasskey finishRegistration(Sysuser sysuser, JSONObject credential, String deviceName) throws AuthException;

    /**
     * 生成认证挑战；discoverable 为 true 时不带 allowCredentials，支持无用户名登录
     */
    Map<String, Object> createAuthenticationOptions(Sysuser sysuser) throws AuthException;

    /**
     * 校验断言结果，返回凭据所属账号
     */
    Sysuser finishAuthentication(JSONObject credential, String expectedUserUuid) throws AuthException;

    List<SysPasskey> listPasskeys(Integer userId);

    /**
     * 解绑指定账号下的 Passkey
     */
    boolean removePasskey(Long id, Integer userId);
}
