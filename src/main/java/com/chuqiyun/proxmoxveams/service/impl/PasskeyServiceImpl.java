package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.common.AuthException;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.entity.SysPasskey;
import com.chuqiyun.proxmoxveams.entity.Sysuser;
import com.chuqiyun.proxmoxveams.service.PasskeyService;
import com.chuqiyun.proxmoxveams.service.SysPasskeyService;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.utils.Base64UrlUtil;
import com.chuqiyun.proxmoxveams.utils.WebAuthnUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: 星禾
 * @Description: Passkey 注册与认证实现
 * @DateTime: 2026/10/17 16:00
 *
 * 挑战只保存在服务端内存，一次性使用且 5 分钟过期，避免前端回传挑战被篡改。
 */
@Slf4j
@Service("passkeyService")
public class PasskeyServiceImpl implements PasskeyService {
    private static final String CLIENT_DATA_CREATE = "webauthn.create";
    private static final String CLIENT_DATA_GET = "webauthn.get";
    private static final long CHALLENGE_TTL_MILLIS = 5 * 60 * 1000L;
    private static final int CHALLENGE_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 挑战缓存，key 为挑战原文，value 为对应的注册/认证上下文
     */
    private final Map<String, PendingChallenge> challenges = new ConcurrentHashMap<>();

    @Resource
    private SysPasskeyService sysPasskeyService;
    @Resource
    private SysuserService sysuserService;

    @Value("${config.passkey.rp_id:localhost}")
    private String rpId;
    @Value("${config.passkey.rp_name:QimenIDC}")
    private String rpName;
    @Value("${config.passkey.origin:}")
    private String origin;

    /**
     * 注册或认证挑战的上下文
     */
    private record PendingChallenge(String type, String userUuid, long expiresAt) {
    }

    @Override
    public Map<String, Object> createRegistrationOptions(Sysuser sysuser) throws AuthException {
        if (Objects.isNull(sysuser)) {
            throw new AuthException(ResponseResult.RespCode.UNAUTHORIZED);
        }
        String challenge = newChallenge("registration", sysuser.getUuid());
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("challenge", challenge);
        options.put("rp", Map.of("id", rpId, "name", rpName));
        options.put("user", Map.of(
                "id", Base64UrlUtil.encode(sysuser.getUuid().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "name", sysuser.getUsername(),
                "displayName", StringUtils.defaultIfBlank(sysuser.getName(), sysuser.getUsername())));
        options.put("pubKeyCredParams", List.of(
                Map.of("type", "public-key", "alg", WebAuthnUtil.ALG_ES256),
                Map.of("type", "public-key", "alg", WebAuthnUtil.ALG_RS256)));
        options.put("timeout", CHALLENGE_TTL_MILLIS);
        options.put("attestation", "none");
        // 只允许新凭据，避免把已有凭据再次注册成新记录
        options.put("excludeCredentials", buildExcludeCredentials(sysuser.getId()));
        Map<String, Object> selection = new LinkedHashMap<>();
        selection.put("residentKey", "preferred");
        selection.put("userVerification", "preferred");
        options.put("authenticatorSelection", selection);
        return options;
    }

    @Override
    public SysPasskey finishRegistration(Sysuser sysuser, JSONObject credential, String deviceName) throws AuthException {
        if (Objects.isNull(sysuser)) {
            throw new AuthException(ResponseResult.RespCode.UNAUTHORIZED);
        }
        byte[] attestationObject = readField(credential, "attestationObject");
        byte[] clientDataJson = readField(credential, "clientDataJSON");
        checkOrigin(clientDataJson);
        WebAuthnUtil.ClientData clientData = WebAuthnUtil.parseClientData(clientDataJson, CLIENT_DATA_CREATE);
        PendingChallenge pending = consumeChallenge("registration", clientData.getChallenge(), sysuser.getUuid());

        byte[] authData = WebAuthnUtil.extractAuthData(attestationObject);
        WebAuthnUtil.AuthData parsed = WebAuthnUtil.parseAuthData(authData);
        WebAuthnUtil.verifyRpId(parsed.getRpIdHash(), rpId);
        if (!parsed.isUserPresent() || !parsed.hasAttestedCredentialData()) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_FAIL);
        }
        if (parsed.getCredentialId().length == 0 || parsed.getCredentialPublicKey().length == 0) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_FAIL);
        }
        String credentialId = Base64UrlUtil.encode(parsed.getCredentialId());
        if (!Objects.isNull(sysPasskeyService.getByCredentialId(credentialId))) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_FAIL);
        }

        SysPasskey passkey = new SysPasskey();
        passkey.setUserid(sysuser.getId());
        passkey.setCredentialId(credentialId);
        passkey.setPublicKey(Base64UrlUtil.encode(parsed.getCredentialPublicKey()));
        passkey.setSignCount(parsed.getSignCount());
        passkey.setBackupEligible(parsed.isBackupEligible() ? 1 : 0);
        passkey.setBackupState(parsed.isBackupState() ? 1 : 0);
        passkey.setAlgorithm(parsed.getAlgorithm());
        passkey.setDeviceName(StringUtils.abbreviate(
                StringUtils.defaultIfBlank(deviceName, "Passkey"), 100));
        passkey.setAaguid(Base64UrlUtil.encode(parsed.getAaguid()));
        passkey.setCreateTime(System.currentTimeMillis());
        if (!sysPasskeyService.save(passkey)) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_FAIL);
        }

        if (!isPasskeyEnabled(sysuser)) {
            sysuser.setPasskeyEnabled(1);
            sysuserService.updateMfaSettings(sysuser);
        }
        log.info("管理员 {} 绑定 Passkey 成功, credentialId={}", sysuser.getUsername(), credentialId);
        return passkey;
    }

    @Override
    public Map<String, Object> createAuthenticationOptions(Sysuser sysuser) throws AuthException {
        String challenge = newChallenge("authentication",
                Objects.isNull(sysuser) ? null : sysuser.getUuid());
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("challenge", challenge);
        options.put("rpId", rpId);
        options.put("timeout", CHALLENGE_TTL_MILLIS);
        options.put("userVerification", "preferred");
        if (!Objects.isNull(sysuser)) {
            options.put("allowCredentials", buildExcludeCredentials(sysuser.getId()));
        }
        return options;
    }

    @Override
    public Sysuser finishAuthentication(JSONObject credential, String expectedUserUuid) throws AuthException {
        byte[] authenticatorData = readField(credential, "authenticatorData");
        byte[] clientDataJson = readField(credential, "clientDataJSON");
        byte[] signature = readField(credential, "signature");
        byte[] userHandle = readOptionalField(credential, "userHandle");
        String rawId = credential.getString("rawId");
        if (StringUtils.isBlank(rawId)) {
            rawId = credential.getString("id");
        }
        checkOrigin(clientDataJson);
        WebAuthnUtil.ClientData clientData = WebAuthnUtil.parseClientData(clientDataJson, CLIENT_DATA_GET);
        consumeChallenge("authentication", clientData.getChallenge(), expectedUserUuid);

        if (StringUtils.isBlank(rawId)) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_FAIL);
        }
        SysPasskey passkey;
        try {
            passkey = sysPasskeyService.getByCredentialId(Base64UrlUtil.encode(Base64UrlUtil.decode(rawId)));
        } catch (IllegalArgumentException e) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_FAIL);
        }
        if (Objects.isNull(passkey)) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_FAIL);
        }
        Sysuser sysuser = sysuserService.getById(passkey.getUserid());
        if (Objects.isNull(sysuser)) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_FAIL);
        }
        // 挑战绑定账号时校验一致性，避免跨账号重放
        if (StringUtils.isNotBlank(expectedUserUuid) && !expectedUserUuid.equals(sysuser.getUuid())) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_FAIL);
        }
        if (userHandle != null && userHandle.length > 0
                && !sysuser.getUuid().equals(new String(userHandle, java.nio.charset.StandardCharsets.UTF_8))) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_FAIL);
        }

        WebAuthnUtil.AuthData parsed = WebAuthnUtil.parseAuthData(authenticatorData);
        WebAuthnUtil.verifyRpId(parsed.getRpIdHash(), rpId);
        if (!parsed.isUserPresent()) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_FAIL);
        }
        boolean verified = WebAuthnUtil.verifySignature(passkey.getAlgorithm(),
                Base64UrlUtil.decode(passkey.getPublicKey()),
                authenticatorData, clientData.getHash(), signature);
        if (!verified) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_FAIL);
        }
        long storedCount = Objects.isNull(passkey.getSignCount()) ? 0L : passkey.getSignCount();
        if (parsed.getSignCount() > 0 && parsed.getSignCount() <= storedCount) {
            // 计数器回退可能意味着凭据被克隆
            log.warn("Passkey 计数器异常, credentialId={}, stored={}, received={}",
                    passkey.getCredentialId(), storedCount, parsed.getSignCount());
            throw new AuthException(ResponseResult.RespCode.PASSKEY_FAIL);
        }
        long newCount = Math.max(parsed.getSignCount(), storedCount);
        sysPasskeyService.touchLastUsed(passkey, newCount, System.currentTimeMillis());
        return sysuser;
    }

    @Override
    public List<SysPasskey> listPasskeys(Integer userId) {
        return sysPasskeyService.listByUserId(userId);
    }

    @Override
    public boolean removePasskey(Long id, Integer userId) {
        return sysPasskeyService.removePasskey(id, userId);
    }

    private boolean isPasskeyEnabled(Sysuser sysuser) {
        return Objects.equals(sysuser.getPasskeyEnabled(), 1);
    }

    private List<Map<String, Object>> buildExcludeCredentials(Integer userId) {
        List<Map<String, Object>> credentials = new ArrayList<>();
        for (SysPasskey passkey : sysPasskeyService.listByUserId(userId)) {
            credentials.add(Map.of("type", "public-key", "id", passkey.getCredentialId()));
        }
        return credentials;
    }

    private String newChallenge(String type, String userUuid) {
        purgeExpired();
        byte[] raw = new byte[CHALLENGE_BYTES];
        RANDOM.nextBytes(raw);
        String challenge = Base64UrlUtil.encode(raw);
        challenges.put(challenge, new PendingChallenge(type, userUuid,
                System.currentTimeMillis() + CHALLENGE_TTL_MILLIS));
        return challenge;
    }

    private PendingChallenge consumeChallenge(String type, String challenge, String expectedUserUuid) {
        if (StringUtils.isBlank(challenge)) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_CHALLENGE_EXPIRED);
        }
        String normalized;
        try {
            normalized = Base64UrlUtil.encode(Base64UrlUtil.decode(challenge));
        } catch (IllegalArgumentException e) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_CHALLENGE_EXPIRED);
        }
        PendingChallenge pending = challenges.remove(normalized);
        if (Objects.isNull(pending) || pending.expiresAt() < System.currentTimeMillis()
                || !type.equals(pending.type())) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_CHALLENGE_EXPIRED);
        }
        if (!Objects.isNull(pending.userUuid()) && !Objects.isNull(expectedUserUuid)
                && !pending.userUuid().equals(expectedUserUuid)) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_CHALLENGE_EXPIRED);
        }
        return pending;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        challenges.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
    }

    /**
     * 配置了 origin 时校验来源，未配置则跳过（兼容反向代理部署）
     */
    private void checkOrigin(byte[] clientDataJson) {
        if (StringUtils.isBlank(origin)) {
            return;
        }
        JSONObject clientData = JSONObject.parseObject(new String(clientDataJson, java.nio.charset.StandardCharsets.UTF_8));
        String clientOrigin = clientData.getString("origin");
        if (!StringUtils.equalsAny(clientOrigin, StringUtils.split(origin, ','))) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_FAIL);
        }
    }

    private byte[] readField(JSONObject credential, String field) throws AuthException {
        byte[] value = readOptionalField(credential, field);
        if (value == null || value.length == 0) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_FAIL);
        }
        return value;
    }

    private byte[] readOptionalField(JSONObject credential, String field) {
        if (Objects.isNull(credential)) {
            return null;
        }
        String value = credential.getString(field);
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Base64UrlUtil.decode(value);
        } catch (IllegalArgumentException e) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_FAIL);
        }
    }

}
