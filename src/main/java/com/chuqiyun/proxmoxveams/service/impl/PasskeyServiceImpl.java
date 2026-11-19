package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONException;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.annotation.PostConstruct;
import java.net.URI;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * @Author: 星禾
 * @Description: Passkey 注册与认证实现
 * @DateTime: 2026/10/17 16:00
 *
 * 挑战存入共享数据库，一次性消费且 5 分钟过期，支持主备切换。
 */
@Slf4j
@Service("passkeyService")
public class PasskeyServiceImpl implements PasskeyService {
    private static final String CLIENT_DATA_CREATE = "webauthn.create";
    private static final String CLIENT_DATA_GET = "webauthn.get";
    private static final long CHALLENGE_TTL_MILLIS = 5 * 60 * 1000L;
    private static final int CHALLENGE_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Resource
    private JdbcTemplate jdbc;

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
    /** 管理员 Passkey 默认要求设备完成 PIN、生物识别或解锁验证。 */
    @Value("${config.passkey.require_user_verification:true}")
    private boolean requireUserVerification = true;

    /**
     * Fail fast on values that browsers will reject anyway.  The RP ID is a
     * domain only; the Origin is the exact browser origin and may include a
     * non-default HTTPS port.
     */
    @PostConstruct
    void validateConfiguration() {
        rpId = StringUtils.trimToEmpty(rpId).toLowerCase(Locale.ROOT);
        if (StringUtils.isBlank(rpId) || rpId.contains("://") || rpId.contains("/")
                || rpId.contains(":") || rpId.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalStateException("config.passkey.rp_id must be a domain without scheme, port or path");
        }
        origin = normalizeOrigins(origin);
        if (StringUtils.isBlank(origin)) {
            log.warn("Passkey Origin 校验未启用；生产环境请设置 config.passkey.origin 或 PASSKEY_ORIGIN");
            return;
        }
        for (String value : StringUtils.split(origin, ',')) {
            validateOrigin(value);
        }
    }

    private String normalizeOrigins(String configured) {
        String[] values = StringUtils.split(StringUtils.defaultString(configured), ',');
        if (values == null || values.length == 0) {
            return "";
        }
        return java.util.Arrays.stream(values)
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private void validateOrigin(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = StringUtils.defaultString(uri.getScheme()).toLowerCase(Locale.ROOT);
            boolean localHttp = "http".equals(scheme)
                    && ("localhost".equalsIgnoreCase(uri.getHost())
                    || "127.0.0.1".equals(uri.getHost())
                    || "[::1]".equals(uri.getHost()));
            if (!("https".equals(scheme) || localHttp) || StringUtils.isBlank(uri.getHost())
                    || uri.getUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || StringUtils.isNotBlank(uri.getRawPath())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException | JSONException e) {
            throw new IllegalStateException("config.passkey.origin must be an HTTPS origin without path/query: " + value);
        }
    }

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
        selection.put("userVerification", requireUserVerification ? "required" : "preferred");
        options.put("authenticatorSelection", selection);
        return options;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysPasskey finishRegistration(Sysuser sysuser, JSONObject credential, String deviceName) throws AuthException {
        try {
            return finishRegistrationInternal(sysuser, credential, deviceName);
        } catch (AuthException e) {
            throw e;
        } catch (IllegalArgumentException | JSONException e) {
            // Malformed WebAuthn/CBOR/base64 input is an authentication failure,
            // not an internal server error.
            log.debug("Passkey 注册数据校验失败", e);
            throw new AuthException(ResponseResult.RespCode.PASSKEY_FAIL);
        }
    }

    private SysPasskey finishRegistrationInternal(Sysuser sysuser, JSONObject credential, String deviceName) throws AuthException {
        if (Objects.isNull(sysuser)) {
            throw new AuthException(ResponseResult.RespCode.UNAUTHORIZED);
        }
        // navigator.credentials.create() puts binary fields under response;
        // accept that standard shape and the historical flattened payload.
        byte[] attestationObject = readCredentialField(credential, "attestationObject");
        byte[] clientDataJson = readCredentialField(credential, "clientDataJSON");
        checkOrigin(clientDataJson);
        WebAuthnUtil.ClientData clientData = WebAuthnUtil.parseClientData(clientDataJson, CLIENT_DATA_CREATE);
        // 与管理员重置共用账号行锁，保证消费挑战和写入凭据不会跨过一次重置。
        if (jdbc.queryForList("select id from sysuser where id = ? for update", Integer.class, sysuser.getId()).isEmpty()) {
            throw new AuthException(ResponseResult.RespCode.UNAUTHORIZED);
        }
        consumeChallenge("registration", clientData.getChallenge(), sysuser.getUuid());

        byte[] authData = WebAuthnUtil.extractAuthData(attestationObject);
        WebAuthnUtil.AuthData parsed = WebAuthnUtil.parseAuthData(authData);
        WebAuthnUtil.verifyRpId(parsed.getRpIdHash(), rpId);
        if (!parsed.isUserPresent() || (requireUserVerification && !parsed.isUserVerified())
                || !parsed.hasAttestedCredentialData()) {
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

        // 只更新 Passkey 状态，避免并发策略修改被请求开始时的旧值覆盖。
        jdbc.update("update sysuser set passkey_enabled = 1 where id = ?", sysuser.getId());
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
        options.put("userVerification", requireUserVerification ? "required" : "preferred");
        if (!Objects.isNull(sysuser)) {
            options.put("allowCredentials", buildExcludeCredentials(sysuser.getId()));
        }
        return options;
    }

    @Override
    public Sysuser finishAuthentication(JSONObject credential, String expectedUserUuid) throws AuthException {
        try {
            return finishAuthenticationInternal(credential, expectedUserUuid);
        } catch (AuthException e) {
            throw e;
        } catch (IllegalArgumentException | JSONException e) {
            // Malformed WebAuthn input must not become a 500 response.
            log.debug("Passkey 认证数据校验失败", e);
            throw new AuthException(ResponseResult.RespCode.PASSKEY_FAIL);
        }
    }

    private Sysuser finishAuthenticationInternal(JSONObject credential, String expectedUserUuid) throws AuthException {
        // navigator.credentials.get() returns response.authenticatorData,
        // response.clientDataJSON, response.signature and response.userHandle.
        byte[] authenticatorData = readCredentialField(credential, "authenticatorData");
        byte[] clientDataJson = readCredentialField(credential, "clientDataJSON");
        byte[] signature = readCredentialField(credential, "signature");
        byte[] userHandle = readOptionalCredentialField(credential, "userHandle");
        String rawId = credential.getString("rawId");
        if (StringUtils.isBlank(rawId)) {
            rawId = credential.getString("id");
        }
        checkOrigin(clientDataJson);
        WebAuthnUtil.ClientData clientData = WebAuthnUtil.parseClientData(clientDataJson, CLIENT_DATA_GET);
        PendingChallenge pending = consumeChallenge("authentication", clientData.getChallenge(), expectedUserUuid);

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
        // 账号读取前可能刚完成重置，再确认凭据仍存在；之后的重置由登录会话比较写入拦截。
        if (sysPasskeyService.getByCredentialId(passkey.getCredentialId()) == null) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_FAIL);
        }
        // 挑战绑定账号时校验一致性，避免跨账号重放
        String challengeUserUuid = StringUtils.defaultIfBlank(pending.userUuid(), expectedUserUuid);
        if (StringUtils.isNotBlank(challengeUserUuid) && !challengeUserUuid.equals(sysuser.getUuid())) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_FAIL);
        }
        if (userHandle != null && userHandle.length > 0
                && !sysuser.getUuid().equals(new String(userHandle, java.nio.charset.StandardCharsets.UTF_8))) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_FAIL);
        }

        WebAuthnUtil.AuthData parsed = WebAuthnUtil.parseAuthData(authenticatorData);
        WebAuthnUtil.verifyRpId(parsed.getRpIdHash(), rpId);
        if (!parsed.isUserPresent() || (requireUserVerification && !parsed.isUserVerified())) {
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
        jdbc.update("insert into sys_passkey_challenge (challenge, type, user_uuid, expires_at) values (?, ?, ?, ?)",
                challenge, type, userUuid, System.currentTimeMillis() + CHALLENGE_TTL_MILLIS);
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
        List<PendingChallenge> rows = jdbc.query(
                "select type, user_uuid, expires_at from sys_passkey_challenge where challenge = ?",
                (rs, rowNum) -> new PendingChallenge(rs.getString("type"), rs.getString("user_uuid"),
                        rs.getLong("expires_at")), normalized);
        PendingChallenge pending = rows.isEmpty() ? null : rows.get(0);
        if (Objects.isNull(pending) || pending.expiresAt() < System.currentTimeMillis()
                || !type.equals(pending.type())) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_CHALLENGE_EXPIRED);
        }
        if (!Objects.isNull(pending.userUuid()) && !Objects.isNull(expectedUserUuid)
                && !pending.userUuid().equals(expectedUserUuid)) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_CHALLENGE_EXPIRED);
        }
        // 条件删除保证并发提交和跨实例重放只有一次能成功。
        if (jdbc.update("delete from sys_passkey_challenge where challenge = ? and expires_at >= ?",
                normalized, System.currentTimeMillis()) != 1) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_CHALLENGE_EXPIRED);
        }
        return pending;
    }

    private void purgeExpired() {
        jdbc.update("delete from sys_passkey_challenge where expires_at < ?", System.currentTimeMillis());
    }

    /**
     * Passkey 必须绑定到浏览器真实来源。未配置 origin 时拒绝认证，
     * 避免生产环境因漏配而跳过 Origin 校验。
     */
    private void checkOrigin(byte[] clientDataJson) {
        if (StringUtils.isBlank(origin)) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_FAIL);
        }
        JSONObject clientData = JSONObject.parseObject(new String(clientDataJson, java.nio.charset.StandardCharsets.UTF_8));
        String clientOrigin = clientData.getString("origin");
        String[] allowedOrigins = StringUtils.split(origin, ',');
        if (allowedOrigins == null || allowedOrigins.length == 0
                || StringUtils.isBlank(clientOrigin)
                || !java.util.Arrays.stream(allowedOrigins)
                .map(String::trim)
                .anyMatch(clientOrigin::equals)) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_FAIL);
        }
    }

    private byte[] readCredentialField(JSONObject credential, String field) throws AuthException {
        byte[] value = readOptionalCredentialField(credential, field);
        if (value == null || value.length == 0) {
            throw new AuthException(ResponseResult.RespCode.PASSKEY_FAIL);
        }
        return value;
    }

    private byte[] readOptionalCredentialField(JSONObject credential, String field) {
        if (credential == null) {
            return null;
        }
        byte[] value = readOptionalField(credential, field);
        if (value != null && value.length > 0) {
            return value;
        }
        return readOptionalField(credential.getJSONObject("response"), field);
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
