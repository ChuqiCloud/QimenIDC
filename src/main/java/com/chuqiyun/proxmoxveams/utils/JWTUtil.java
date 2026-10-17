package com.chuqiyun.proxmoxveams.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

/**
 * @author mryunqi
 * @date 2023/4/14
 */
@Slf4j
public class JWTUtil {

    // 过期时间120分钟
    private static final long EXPIRE_TIME = 120*60*1000;

    /**
     * 校验token是否正确
     * @param token 密钥
     * @return 是否正确
     */
    public static boolean verify(String token, String uuid,String secret) {
        try {
            if (uuid == null || uuid.isBlank()) {
                return false;
            }
            Algorithm algorithm = Algorithm.HMAC256(getSecretKey(secret));
            JWTVerifier verifier = JWT.require(algorithm)
                    .withClaim("uuid", uuid)
                    .build();
            DecodedJWT jwt = verifier.verify(token);
            // MFA 临时票据不含正式会话密文，不能作为业务登录凭据使用。
            String session = jwt.getClaim("secret").asString();
            return jwt.getClaim("scope").isMissing() && session != null && !session.isBlank();
        } catch (Exception exception) {
            return false;
        }
    }

    /**
     * 获得token中的信息无需secret解密也能获得
     * @return token中包含的用户名
     */
    public static String getUsername(String token,String secret) {
        try {
            DecodedJWT jwt = JWT.decode(token);
            boolean verify = verify(token,jwt.getClaim("uuid").asString(),secret);
            if (!verify){
                return null;
            }
            return jwt.getClaim("uuid").asString();
        } catch (Exception e) {
            return null;
        }
    }


    public static Long getTokenData(String token,String secret){
        try {
            DecodedJWT jwt = JWT.decode(token);
            boolean verify = verify(token,jwt.getClaim("uuid").asString(),secret);
            if (!verify){
                return null;
            }
            String secretData = jwt.getClaim("secret").asString();
            return Long.valueOf(AESUtil.decrypt(secretData,secret));
        } catch (Exception e) {
            return null;
        }
    }

    /** token 必须与数据库当前存储的会话标记完全一致。 */
    public static boolean isCurrentSession(Long tokenSession, Long storedSession) {
        return tokenSession != null && tokenSession > 0 && tokenSession.equals(storedSession);
    }

    /**
     * 生成签名
     * @param uuid 用户唯一uuid
     * @return 加密的token
     */
    public static String sign(String uuid,String secret) {
        return sign(uuid, secret, System.currentTimeMillis());
    }

    /** 绑定数据库的会话标记，不能再独立读取一次系统时间。 */
    public static String sign(String uuid, String secret, long session) {
        Date date = new Date(System.currentTimeMillis()+EXPIRE_TIME);
        Algorithm algorithm = Algorithm.HMAC256(getSecretKey(secret));
        // 附带username信息
        return JWT.create()
                .withClaim("uuid", uuid)
                .withClaim("secret",getSecretDate(secret, session))
                .withExpiresAt(date)
                .sign(algorithm);
    }

    private static String getSecretDate(String secret, long time){
        String encrypted;
        try {
            encrypted = AESUtil.encrypt(String.valueOf(time),secret);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return encrypted;
    }

    private static String getSecretKey(String secret) {
        return Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8));
    }
}
