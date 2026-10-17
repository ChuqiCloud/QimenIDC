package com.chuqiyun.proxmoxveams.controller.admin;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.AuthException;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.entity.SysPasskey;
import com.chuqiyun.proxmoxveams.entity.Sysuser;
import com.chuqiyun.proxmoxveams.security.TotpSecretCipher;
import com.chuqiyun.proxmoxveams.service.PasskeyService;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.utils.ServletUtil;
import com.chuqiyun.proxmoxveams.utils.TotpUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @Author: 星禾
 * @Description: 管理员个人页：资料、改密、TOTP、Passkey 绑定与二步验证策略
 * @DateTime: 2026/10/17 17:00
 *
 * 所有接口均作用于当前登录账号自身。
 */
@RestController
@RequestMapping("/{adminPath}")
public class SysProfileController {
    private static final String MFA_NONE = "none";
    private static final String MFA_TOTP = "totp";
    private static final String MFA_PASSKEY = "passkey";
    private static final int TOTP_DRIFT_STEPS = 1;

    @Value("${config.secret}")
    private String secret;
    @Value("${info.application.name:QimenIDC}")
    private String issuer;

    @Resource
    private SysuserService sysuserService;
    @Resource
    private PasskeyService passkeyService;

    /**
     * 当前账号资料与安全设置概览
     */
    @AdminApiCheck
    @GetMapping("/profile")
    public ResponseResult<Object> profile(HttpServletRequest request) throws UnauthorizedException {
        Sysuser sysuser = currentUser(request);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("uuid", sysuser.getUuid());
        profile.put("username", sysuser.getUsername());
        profile.put("name", sysuser.getName());
        profile.put("phone", sysuser.getPhone());
        profile.put("email", sysuser.getEmail());
        profile.put("logindate", sysuser.getLogindate());
        profile.put("mfaType", normalizeMfaType(sysuser.getMfaType()));
        profile.put("totpBound", StringUtils.isNotBlank(sysuser.getTotpSecret())
                && !Objects.isNull(sysuser.getTotpConfirmedTime()));
        int passkeyCount = passkeyService.listPasskeys(sysuser.getId()).size();
        profile.put("passkeyEnabled", passkeyCount > 0);
        profile.put("passkeyCount", passkeyCount);
        return ResponseResult.ok(profile);
    }

    /**
     * 修改自身资料（不含密码）
     */
    @AdminApiCheck
    @PostMapping("/profile/update")
    public ResponseResult<String> updateProfile(@RequestBody JSONObject param,
                                                HttpServletRequest request) throws UnauthorizedException {
        Sysuser sysuser = currentUser(request);
        String username = param.getString("username");
        if (StringUtils.isNotBlank(username) && !sysuser.getUsername().equals(username)) {
            Sysuser exist = sysuserService.getSysuserByUsername(username);
            if (!Objects.isNull(exist)) {
                return ResponseResult.fail(ResponseResult.RespCode.REG_EXISTING_USER);
            }
            sysuser.setUsername(username);
        }
        sysuser.setName(param.getString("name"));
        sysuser.setPhone(param.getString("phone"));
        sysuser.setEmail(param.getString("email"));
        return sysuserService.updateById(sysuser)
                ? ResponseResult.ok("资料修改成功！")
                : ResponseResult.fail("资料修改失败！");
    }

    /**
     * 修改自己的密码（管理员自助重置）
     */
    @AdminApiCheck
    @PostMapping("/profile/password")
    public ResponseResult<String> resetPassword(@RequestBody JSONObject param,
                                                HttpServletRequest request) throws UnauthorizedException {
        Sysuser sysuser = currentUser(request);
        String message = sysuserService.resetOwnPassword(sysuser.getId(),
                param.getString("oldPassword"), param.getString("newPassword"));
        if (message != null) {
            return ResponseResult.fail(ResponseResult.RespCode.OAUTH_PASSWORD_FAIL.getCode(), message);
        }
        return ResponseResult.ok("密码修改成功，请重新登录！");
    }

    /**
     * 开始绑定 TOTP：返回密钥与 otpauth 链接，需再用动态码确认
     */
    @AdminApiCheck
    @PostMapping("/profile/totp/bind")
    public ResponseResult<Object> bindTotp(HttpServletRequest request) throws UnauthorizedException {
        Sysuser sysuser = currentUser(request);
        String secretKey = TotpUtil.generateSecret();
        // 未确认前先落库，确认后才写入 totp_confirmed_time
        sysuser.setTotpSecret(TotpSecretCipher.encrypt(secretKey));
        sysuser.setTotpConfirmedTime(null);
        if (!sysuserService.updateMfaSettings(sysuser)) {
            return ResponseResult.fail("TOTP 绑定失败，请重试！");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("secret", secretKey);
        result.put("issuer", issuer);
        result.put("account", sysuser.getUsername());
        result.put("digits", TotpUtil.DEFAULT_DIGITS);
        result.put("period", TotpUtil.DEFAULT_PERIOD);
        result.put("otpauthUrl", TotpUtil.buildOtpAuthUrl(issuer, sysuser.getUsername(), secretKey));
        return ResponseResult.ok(result);
    }

    /**
     * 确认 TOTP 绑定，确认后才允许把 totp 作为二步验证方式
     */
    @AdminApiCheck
    @PostMapping("/profile/totp/confirm")
    public ResponseResult<Object> confirmTotp(@RequestBody JSONObject param,
                                              HttpServletRequest request) throws UnauthorizedException {
        Sysuser sysuser = currentUser(request);
        if (StringUtils.isBlank(sysuser.getTotpSecret())) {
            return ResponseResult.fail(ResponseResult.RespCode.MFA_FAIL.getCode(), "请先生成 TOTP 密钥");
        }
        long step = verifyTotp(sysuser, param.getString("code"));
        if (step < 0) {
            return ResponseResult.fail(ResponseResult.RespCode.MFA_FAIL);
        }
        if (!sysuserService.consumeTotpStep(sysuser.getId(), step)) {
            return ResponseResult.fail(ResponseResult.RespCode.MFA_FAIL.getCode(), "该动态码已被使用，请等待下一个");
        }
        sysuser.setTotpConfirmedTime(System.currentTimeMillis());
        // 已有的 totp_secret 与 mfa_type 原样写回，是否启用由 /profile/mfa 决定
        if (!sysuserService.updateMfaSettings(sysuser)) {
            return ResponseResult.fail("TOTP 确认失败，请重试！");
        }
        return ResponseResult.ok("TOTP 绑定成功！");
    }

    /**
     * 解绑 TOTP，同时关闭 totp 二步验证
     */
    @AdminApiCheck
    @PostMapping("/profile/totp/unbind")
    public ResponseResult<String> unbindTotp(@RequestBody JSONObject param,
                                             HttpServletRequest request) throws UnauthorizedException {
        Sysuser sysuser = currentUser(request);
        // 没有其它可用二步验证方式时，要求输入当前密码，避免会话被劫持后直接解绑
        if (!hasOtherFactor(sysuser)) {
            if (!sysuserService.verifyPassword(sysuser.getId(), param.getString("password"))) {
                return ResponseResult.fail(ResponseResult.RespCode.OAUTH_PASSWORD_FAIL.getCode(), "密码错误，无法解绑");
            }
        }
        sysuser.setTotpSecret(null);
        sysuser.setTotpConfirmedTime(null);
        sysuser.setTotpLastStep(null);
        if (MFA_TOTP.equals(normalizeMfaType(sysuser.getMfaType()))) {
            sysuser.setMfaType(MFA_NONE);
        }
        return sysuserService.updateMfaSettings(sysuser)
                ? ResponseResult.ok("TOTP 已解绑！")
                : ResponseResult.fail("TOTP 解绑失败！");
    }

    /**
     * 设置二步验证方式：none / totp / passkey
     */
    @AdminApiCheck
    @PostMapping("/profile/mfa")
    public ResponseResult<Object> updateMfaType(@RequestBody JSONObject param,
                                                HttpServletRequest request) throws UnauthorizedException {
        Sysuser sysuser = currentUser(request);
        String mfaType = StringUtils.defaultIfBlank(param.getString("mfaType"), MFA_NONE).trim().toLowerCase();
        if (MFA_NONE.equals(mfaType)) {
            sysuser.setMfaType(MFA_NONE);
            return sysuserService.updateMfaSettings(sysuser)
                    ? ResponseResult.ok("已关闭二步验证！")
                    : ResponseResult.fail("设置失败！");
        }
        if (MFA_TOTP.equals(mfaType)) {
            if (StringUtils.isBlank(sysuser.getTotpSecret()) || Objects.isNull(sysuser.getTotpConfirmedTime())) {
                return ResponseResult.fail(ResponseResult.RespCode.MFA_FAIL.getCode(), "请先绑定并确认 TOTP");
            }
            sysuser.setMfaType(MFA_TOTP);
            return sysuserService.updateMfaSettings(sysuser)
                    ? ResponseResult.ok("已启用 TOTP 二步验证！")
                    : ResponseResult.fail("设置失败！");
        }
        if (MFA_PASSKEY.equals(mfaType)) {
            if (passkeyService.listPasskeys(sysuser.getId()).isEmpty()) {
                return ResponseResult.fail(ResponseResult.RespCode.PASSKEY_NOT_BOUND);
            }
            sysuser.setMfaType(MFA_PASSKEY);
            return sysuserService.updateMfaSettings(sysuser)
                    ? ResponseResult.ok("已启用 Passkey 二步验证！")
                    : ResponseResult.fail("设置失败！");
        }
        return ResponseResult.fail(ResponseResult.RespCode.DATA_ERROR.getCode(), "不支持的二步验证方式");
    }

    /**
     * Passkey 列表
     */
    @AdminApiCheck
    @GetMapping("/profile/passkey/list")
    public ResponseResult<Object> listPasskeys(HttpServletRequest request) throws UnauthorizedException {
        Sysuser sysuser = currentUser(request);
        List<SysPasskey> passkeys = passkeyService.listPasskeys(sysuser.getId());
        JSONArray array = new JSONArray();
        for (SysPasskey passkey : passkeys) {
            JSONObject item = new JSONObject();
            item.put("id", passkey.getId());
            item.put("deviceName", passkey.getDeviceName());
            item.put("createTime", passkey.getCreateTime());
            item.put("lastUsedTime", passkey.getLastUsedTime());
            item.put("backupEligible", Objects.equals(1, passkey.getBackupEligible()));
            array.add(item);
        }
        return ResponseResult.ok(array);
    }

    /**
     * Passkey 注册挑战
     */
    @AdminApiCheck
    @PostMapping("/profile/passkey/register/options")
    public ResponseResult<Object> passkeyRegisterOptions(HttpServletRequest request) throws UnauthorizedException {
        try {
            return ResponseResult.ok(passkeyService.createRegistrationOptions(currentUser(request)));
        } catch (AuthException e) {
            return ResponseResult.fail(e.getRespCode());
        }
    }

    /**
     * 提交注册结果，完成 Passkey 绑定
     */
    @AdminApiCheck
    @PostMapping("/profile/passkey/register")
    public ResponseResult<Object> passkeyRegister(@RequestBody JSONObject param,
                                                  HttpServletRequest request) throws UnauthorizedException {
        Sysuser sysuser = currentUser(request);
        try {
            SysPasskey passkey = passkeyService.finishRegistration(sysuser,
                    param.getJSONObject("credential"), param.getString("deviceName"));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", passkey.getId());
            result.put("deviceName", passkey.getDeviceName());
            result.put("mfaType", normalizeMfaType(sysuserService.getById(sysuser.getId()).getMfaType()));
            return ResponseResult.ok(result);
        } catch (AuthException e) {
            return ResponseResult.fail(e.getRespCode());
        }
    }

    /**
     * 解绑指定 Passkey
     */
    @AdminApiCheck
    @PostMapping("/profile/passkey/delete")
    public ResponseResult<Object> deletePasskey(@RequestBody JSONObject param,
                                                HttpServletRequest request) throws UnauthorizedException {
        Sysuser sysuser = currentUser(request);
        Long id = param.getLong("id");
        if (Objects.isNull(id)) {
            return ResponseResult.fail(ResponseResult.RespCode.DATA_ERROR.getCode(), "缺少 Passkey ID");
        }
        if (!passkeyService.listPasskeys(sysuser.getId()).stream().anyMatch(p -> id.equals(p.getId()))) {
            return ResponseResult.fail(ResponseResult.RespCode.PASSKEY_FAIL.getCode(), "Passkey 不存在");
        }
        if (!passkeyService.removePasskey(id, sysuser.getId())) {
            return ResponseResult.fail("解绑失败！");
        }
        // 最后一把 Passkey 解绑后自动关闭 passkey 二步验证
        boolean remaining = !passkeyService.listPasskeys(sysuser.getId()).isEmpty();
        Sysuser fresh = sysuserService.getById(sysuser.getId());
        fresh.setPasskeyEnabled(remaining ? 1 : 0);
        if (!remaining && MFA_PASSKEY.equals(normalizeMfaType(fresh.getMfaType()))) {
            fresh.setMfaType(MFA_NONE);
        }
        sysuserService.updateMfaSettings(fresh);
        return ResponseResult.ok("Passkey 已解绑！");
    }

    /**
     * 解绑后是否还保留其它二步验证方式
     */
    private boolean hasOtherFactor(Sysuser sysuser) {
        return !passkeyService.listPasskeys(sysuser.getId()).isEmpty();
    }

    private Sysuser currentUser(HttpServletRequest request) throws UnauthorizedException {
        Sysuser member = ServletUtil.getSysLoginMember(request, secret);
        if (Objects.isNull(member)) {
            throw new UnauthorizedException();
        }
        Sysuser sysuser = sysuserService.getSysuserByUuid(member.getUuid());
        if (Objects.isNull(sysuser)) {
            throw new UnauthorizedException();
        }
        return sysuser;
    }

    private long verifyTotp(Sysuser sysuser, String code) {
        try {
            String plainSecret = TotpSecretCipher.decrypt(sysuser.getTotpSecret());
            return TotpUtil.verify(plainSecret, code, System.currentTimeMillis() / 1000, TOTP_DRIFT_STEPS);
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }

    private String normalizeMfaType(String mfaType) {
        if (MFA_TOTP.equals(mfaType) || MFA_PASSKEY.equals(mfaType)) {
            return mfaType;
        }
        return MFA_NONE;
    }
}
