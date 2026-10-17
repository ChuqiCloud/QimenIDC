package com.chuqiyun.proxmoxveams.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

/**
 * @Author: 星禾
 * @Description: 二步验证挑战票据
 * @DateTime: 2026/10/17 15:22
 *
 * 密码校验通过后只签发短期票据（默认 5 分钟），票据不能用于接口鉴权，
 * 只有完成 TOTP / Passkey 校验后才签发正式登录 token。
 */
@Service
public class MfaTicketService {
    private static final long TICKET_EXPIRE_MILLIS = 5 * 60 * 1000L;
    private static final String SCOPE = "mfa";
    private final String secret;

    public MfaTicketService(@Value("${config.secret}") String secret) {
        this.secret = secret;
    }

    public String issue(String uuid, String method) {
        Date expiresAt = new Date(System.currentTimeMillis() + TICKET_EXPIRE_MILLIS);
        return JWT.create()
                .withClaim("uuid", uuid)
                .withClaim("scope", SCOPE)
                .withClaim("method", method)
                .withExpiresAt(expiresAt)
                .sign(algorithm());
    }

    /**
     * 校验票据，无效或过期返回 null
     */
    public String resolveUuid(String ticket, String expectedMethod) {
        if (StringUtils.isBlank(ticket)) {
            return null;
        }
        try {
            JWTVerifier verifier = JWT.require(algorithm())
                    .withClaim("scope", SCOPE)
                    .withClaim("method", expectedMethod)
                    .build();
            DecodedJWT jwt = verifier.verify(ticket);
            return StringUtils.defaultIfBlank(jwt.getClaim("uuid").asString(), null);
        } catch (JWTVerificationException | IllegalArgumentException e) {
            return null;
        }
    }

    public long getTicketExpireMillis() {
        return TICKET_EXPIRE_MILLIS;
    }

    private Algorithm algorithm() {
        return Algorithm.HMAC256(Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8)));
    }
}
