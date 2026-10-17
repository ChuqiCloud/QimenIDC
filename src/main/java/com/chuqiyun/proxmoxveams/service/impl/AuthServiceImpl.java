package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.chuqiyun.proxmoxveams.common.AuthException;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.dto.LoginResult;
import com.chuqiyun.proxmoxveams.dto.MfaChallenge;
import com.chuqiyun.proxmoxveams.entity.Sysuser;
import com.chuqiyun.proxmoxveams.security.MfaTicketService;
import com.chuqiyun.proxmoxveams.security.TotpSecretCipher;
import com.chuqiyun.proxmoxveams.service.AuthService;
import com.chuqiyun.proxmoxveams.service.PasskeyService;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.utils.EncryptUtil;
import com.chuqiyun.proxmoxveams.utils.JWTUtil;
import com.chuqiyun.proxmoxveams.utils.TotpUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Objects;

/**
 * @Author: 星禾
 * @Description: 登录流程编排
 * @DateTime: 2026/10/17 16:20
 *
 * 约定：
 * 1. 密码登录成功后若启用了二步验证，只返回挑战票据，不下发 token；
 * 2. 二步验证方式为 totp 时必须再输入动态码；
 * 3. passkey 作为二步验证时，使用 Passkey 完成第二因子；
 * 4. 直接用 Passkey 登录（mfa_type != totp）时视为已完成全部验证，
 *    仅当账号同时启用 TOTP 才继续要求动态码。
 */
@Slf4j
@Service("authService")
public class AuthServiceImpl implements AuthService {
    /**
     * 动态码允许前后各一个时间片（30 秒）偏差
     */
    private static final int TOTP_DRIFT_STEPS = 1;
    private static final String MFA_NONE = "none";
    private static final String MFA_TOTP = "totp";
    private static final String MFA_PASSKEY = "passkey";

    @Value("${config.secret}")
    private String secret;

    @Resource
    private SysuserService sysuserService;
    @Resource
    private PasskeyService passkeyService;
    @Resource
    private MfaTicketService mfaTicketService;

    @Override
    public LoginResult loginWithPassword(String username, String password) throws AuthException {
        if (StringUtils.isBlank(username)) {
            throw new AuthException(ResponseResult.RespCode.LOGIN_NO_ACCOUNT);
        }
        if (StringUtils.isBlank(password)) {
            throw new AuthException(ResponseResult.RespCode.LOGIN_NO_ACCOUNT);
        }
        Sysuser sysuser = sysuserService.getSysuserByUsername(username);
        if (Objects.isNull(sysuser)) {
            throw new AuthException(ResponseResult.RespCode.LOGIN_NO_ACCOUNT);
        }
        if (!EncryptUtil.verifyPassword(password, sysuser.getPassword())) {
            throw new AuthException(ResponseResult.RespCode.LOGIN_FAIL);
        }
        String mfaType = resolveMfaType(sysuser);
        if (MFA_NONE.equals(mfaType)) {
            return LoginResult.done(issueLoginToken(sysuser));
        }
        if (MFA_TOTP.equals(mfaType) && StringUtils.isBlank(sysuser.getTotpSecret())) {
            // TOTP 密钥丢失时不允许降级为单因子登录
            log.warn("管理员 {} 已启用 TOTP 但缺少密钥，登录被拒绝", sysuser.getUsername());
            throw new AuthException(ResponseResult.RespCode.MFA_FAIL);
        }
        return LoginResult.mfaRequired(mfaType, mfaTicketService.issue(sysuser.getUuid(), mfaType));
    }

    @Override
    public String loginWithTotp(String ticket, String code) throws AuthException {
        String uuid = mfaTicketService.resolveUuid(ticket, MFA_TOTP);
        if (StringUtils.isBlank(uuid)) {
            throw new AuthException(ResponseResult.RespCode.MFA_TICKET_INVALID);
        }
        Sysuser sysuser = sysuserService.getSysuserByUuid(uuid);
        if (Objects.isNull(sysuser) || StringUtils.isBlank(sysuser.getTotpSecret())) {
            throw new AuthException(ResponseResult.RespCode.MFA_FAIL);
        }
        String plainSecret;
        try {
            plainSecret = TotpSecretCipher.decrypt(sysuser.getTotpSecret());
        } catch (IllegalArgumentException e) {
            log.warn("管理员 {} 的 TOTP 密钥无法解密", sysuser.getUsername());
            throw new AuthException(ResponseResult.RespCode.MFA_FAIL);
        }
        long step = TotpUtil.verify(plainSecret, code, System.currentTimeMillis() / 1000, TOTP_DRIFT_STEPS);
        if (step < 0) {
            throw new AuthException(ResponseResult.RespCode.MFA_FAIL);
        }
        // 同一时间片只允许使用一次，防止动态码被重放
        if (!sysuserService.consumeTotpStep(sysuser.getId(), step)) {
            throw new AuthException(ResponseResult.RespCode.MFA_FAIL);
        }
        return issueLoginToken(sysuser);
    }

    @Override
    public MfaChallenge passkeyLoginChallenge(String ticket) throws AuthException {
        String uuid = mfaTicketService.resolveUuid(ticket, MFA_PASSKEY);
        if (StringUtils.isBlank(uuid)) {
            throw new AuthException(ResponseResult.RespCode.MFA_TICKET_INVALID);
        }
        Sysuser sysuser = sysuserService.getSysuserByUuid(uuid);
        if (Objects.isNull(sysuser)) {
            throw new AuthException(ResponseResult.RespCode.MFA_TICKET_INVALID);
        }
        if (passkeyService.listPasskeys(sysuser.getId()).isEmpty()) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_NOT_BOUND);
        }
        return buildChallenge(MFA_PASSKEY, ticket, passkeyService.createAuthenticationOptions(sysuser));
    }

    @Override
    public String loginWithPasskey(String ticket, JSONObject credential) throws AuthException {
        String uuid = mfaTicketService.resolveUuid(ticket, MFA_PASSKEY);
        if (StringUtils.isBlank(uuid)) {
            throw new AuthException(ResponseResult.RespCode.MFA_TICKET_INVALID);
        }
        Sysuser sysuser = passkeyService.finishAuthentication(credential, uuid);
        return issueLoginToken(sysuser);
    }

    @Override
    public MfaChallenge passkeyDirectChallenge(String username) throws AuthException {
        Sysuser sysuser = requirePasskeyUser(username);
        return buildChallenge(MFA_PASSKEY, null, passkeyService.createAuthenticationOptions(sysuser));
    }

    @Override
    public LoginResult loginWithPasskeyDirect(String username, JSONObject credential) throws AuthException {
        // 无用户名（discoverable）登录时 username 可以为空，账号由凭据反查
        if (StringUtils.isNotBlank(username)) {
            requirePasskeyUser(username);
        }
        Sysuser authenticated = passkeyService.finishAuthentication(credential, null);
        // Passkey 已作为主认证方式，只有同时启用 TOTP 才需要补一次动态码
        if (MFA_TOTP.equals(resolveMfaType(authenticated))) {
            return LoginResult.mfaRequired(MFA_TOTP,
                    mfaTicketService.issue(authenticated.getUuid(), MFA_TOTP));
        }
        return LoginResult.done(issueLoginToken(authenticated));
    }

    @Override
    public String issueLoginToken(Sysuser sysuser) {
        // 刷新登录时间以吊销旧 token
        UpdateWrapper<Sysuser> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", sysuser.getId()).set("logindate", System.currentTimeMillis());
        sysuserService.update(wrapper);
        return JWTUtil.sign(sysuser.getUuid(), secret);
    }

    /**
     * 账号必须存在且绑定过 Passkey
     */
    private Sysuser requirePasskeyUser(String username) {
        if (StringUtils.isBlank(username)) {
            throw new AuthException(ResponseResult.RespCode.LOGIN_NO_ACCOUNT);
        }
        Sysuser sysuser = sysuserService.getSysuserByUsername(username);
        if (Objects.isNull(sysuser)) {
            throw new AuthException(ResponseResult.RespCode.LOGIN_NO_ACCOUNT);
        }
        if (passkeyService.listPasskeys(sysuser.getId()).isEmpty()) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_NOT_BOUND);
        }
        return sysuser;
    }

    private MfaChallenge buildChallenge(String method, String ticket, Object options) {
        MfaChallenge challenge = new MfaChallenge();
        challenge.setMethod(method);
        challenge.setTicket(ticket);
        challenge.setExpiresIn(mfaTicketService.getTicketExpireMillis());
        challenge.setPublicKeyOptions(options);
        return challenge;
    }

    /**
     * 兼容历史数据：mfa_type 为空时按未启用处理
     */
    private String resolveMfaType(Sysuser sysuser) {
        String mfaType = sysuser.getMfaType();
        if (MFA_TOTP.equals(mfaType) || MFA_PASSKEY.equals(mfaType)) {
            return mfaType;
        }
        return MFA_NONE;
    }
}
