package com.chuqiyun.proxmoxveams.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base32;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * @Author: 星禾
 * @Description: RFC 6238 TOTP 实现，兼容标准认证器（Google Authenticator 等）
 * @DateTime: 2026/10/17 15:10
 */
public final class TotpUtil {
    public static final int DEFAULT_DIGITS = 6;
    public static final int DEFAULT_PERIOD = 30;
    private static final int SECRET_BYTES = 20;
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base32 BASE32 = new Base32();
    private static final int MAX_DRIFT_STEPS = 10;

    private TotpUtil() {
    }

    /**
     * 生成 160bit 随机密钥，返回无填充的大写 Base32（otpauth 标准格式）
     */
    public static String generateSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        RANDOM.nextBytes(secret);
        return BASE32.encodeAsString(secret).replace("=", "");
    }

    /**
     * 计算指定时间片的动态码
     */
    public static String generate(String secret, long epochSecond) {
        return generateAtStep(secret, epochSecond / DEFAULT_PERIOD);
    }

    /**
     * 按时间片计算动态码
     */
    public static String generateAtStep(String secret, long counter) {
        byte[] key = decodeSecret(secret);
        byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % 1000000;
            return String.format(Locale.ROOT, "%06d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("TOTP 计算失败", e);
        }
    }

    public static long currentStep(long epochSecond) {
        return epochSecond / DEFAULT_PERIOD;
    }

    /**
     * 校验动态码并返回命中的时间片，未命中返回 -1；调用方需用返回值做重放防护
     *
     * @param driftSteps 允许的时间片偏差，0 表示仅当前时间片
     */
    public static long verify(String secret, String code, long epochSecond, int driftSteps) {
        String normalized = normalizeCode(code);
        if (normalized == null || secret == null) {
            return -1;
        }
        int drift = Math.max(0, Math.min(driftSteps, MAX_DRIFT_STEPS));
        long currentStep = currentStep(epochSecond);
        for (int offset = -drift; offset <= drift; offset++) {
            long candidateStep = currentStep + offset;
            if (candidateStep < 0) {
                continue;
            }
            String candidate = generateAtStep(secret, candidateStep);
            if (constantTimeEquals(candidate, normalized)) {
                return candidateStep;
            }
        }
        return -1;
    }

    /**
     * 认证器常见输入会带空格分隔，统一去掉空白并校验格式
     */
    public static String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String compact = code.replaceAll("[\\s-]", "");
        if (compact.length() != DEFAULT_DIGITS || !compact.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return compact;
    }

    /**
     * 生成 otpauth 链接，供前端渲染二维码
     */
    public static String buildOtpAuthUrl(String issuer, String account, String secret) {
        String label = urlEncode(issuer) + ":" + urlEncode(account);
        return "otpauth://totp/" + label
                + "?secret=" + secret
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1&digits=" + DEFAULT_DIGITS + "&period=" + DEFAULT_PERIOD;
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /**
     * 历史版本使用 160bit Base64URL（27 字符）；标准 Base32 必须能规范地往返编码。
     * 这样不会把历史值中碰巧只含 Base32 字符的密钥误认为新格式。
     */
    private static byte[] decodeSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("TOTP 密钥为空");
        }
        String normalized = secret.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        if (normalized.matches("[A-Z2-7]+=*")) {
            byte[] decoded = BASE32.decode(normalized);
            if (decoded.length > 0 && BASE32.encodeAsString(decoded).replace("=", "")
                    .equals(normalized.replace("=", ""))) {
                return decoded;
            }
        }
        return Base64UrlUtil.decode(secret);
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }
}
