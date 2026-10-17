package com.chuqiyun.proxmoxveams.security;

/**
 * @Author: 星禾
 * @Description: TOTP 密钥落库前的可逆加密，复用凭据加密器的密钥与格式
 * @DateTime: 2026/10/17 15:20
 */
public final class TotpSecretCipher {
    private TotpSecretCipher() {
    }

    public static String encrypt(String plainSecret) {
        return StoredPasswordCipher.encrypt(plainSecret);
    }

    public static String decrypt(String storedValue) {
        return StoredPasswordCipher.decrypt(storedValue);
    }

    public static boolean isEncrypted(String value) {
        return StoredPasswordCipher.isEncrypted(value);
    }
}
