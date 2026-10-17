package com.chuqiyun.proxmoxveams.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * @Author: 星禾
 * @Description: 数据库凭据字段的可逆加密器
 * @DateTime: 2026/10/17 09:46
 *
 * 密钥故意固化在服务端代码中，以避免通过配置接口暴露；部署时应限制
 * 编译产物和源码访问权限。旧的未加密值可以读取，但所有后续写入都会加密。
 */
public final class StoredPasswordCipher {
    private static final String RESERVED_PREFIX = "QIDENC:";
    private static final String PREFIX = RESERVED_PREFIX + "1:";
    private static final byte[] KEY = Base64.getDecoder().decode("Kz53zS8SQBJsG8RoJd4ul+rxVFlyq6Gwb/uiC0DaYr8=");
    private static final byte[] AAD = PREFIX.getBytes(StandardCharsets.US_ASCII);
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private StoredPasswordCipher() {
    }

    /**
     * 输入始终视为明文，避免用户密码碰巧含有密文前缀时绕过加密。
     * 历史数据迁移应先判断并校验现有密文，不能对数据库原值重复调用本方法。
     */
    public static String encrypt(String plainText) {
        if (plainText == null) {
            return plainText;
        }
        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(AAD);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("凭据加密失败", e);
        }
    }

    /**
     * 无前缀的旧明文保持兼容，带密文前缀的数据必须通过版本和 GCM 完整性校验。
     */
    public static String decrypt(String storedValue) {
        if (storedValue == null || !isEncrypted(storedValue)) {
            return storedValue;
        }
        if (!storedValue.startsWith(PREFIX)) {
            throw new IllegalArgumentException("凭据密文版本无效");
        }
        final byte[] payload;
        try {
            payload = Base64.getDecoder().decode(storedValue.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("凭据密文格式无效", e);
        }
        if (payload.length < IV_LENGTH + 16) {
            throw new IllegalArgumentException("凭据密文长度无效");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(AAD);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("凭据密文校验失败", e);
        }
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(RESERVED_PREFIX);
    }
}
