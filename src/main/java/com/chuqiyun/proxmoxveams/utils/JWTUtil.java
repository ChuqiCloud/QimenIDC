package com.chuqiyun.proxmoxveams.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
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
            Algorithm algorithm = Algorithm.HMAC256(getSecretKey(secret));
            JWTVerifier verifier = JWT.require(algorithm)
                    .withClaim("uuid", uuid)
                    .build();
            DecodedJWT jwt = verifier.verify(token);
            return true;
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
        } catch (JWTDecodeException e) {
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
        } catch (JWTDecodeException e) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 生成签名
     * @param uuid 用户唯一uuid
     * @return 加密的token
     */
    public static String sign(String uuid,String secret) {
        Date date = new Date(System.currentTimeMillis()+EXPIRE_TIME);
        Algorithm algorithm = Algorithm.HMAC256(getSecretKey(secret));
        // 附带username信息
        return JWT.create()
                .withClaim("uuid", uuid)
                .withClaim("secret",getSecretDate(secret))
                .withExpiresAt(date)
                .sign(algorithm);
    }

    private static String getSecretDate(String secret){
        Long time = System.currentTimeMillis();
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
