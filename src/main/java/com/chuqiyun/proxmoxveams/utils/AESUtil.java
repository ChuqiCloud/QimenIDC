package com.chuqiyun.proxmoxveams.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * @author mryunqi
 * @date 2023/4/18
 */
public class AESUtil {
    // 密钥长度
    private static final int KEY_LENGTH = 16;

    // 加密算法
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    /**
     * 对明文进行AES加密
     * @param plainText 明文
     * @return 加密后的密文
     */
    public static String encrypt(String plainText,String secret) throws Exception {
        // 生成密钥
        SecretKeySpec secretKeySpec = generateKey(secret);

        // 生成随机向量
        byte[] iv = generateIV();

        // 创建密码器
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new IvParameterSpec(iv));

        // 加密
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        // 拼接随机向量和密文
        byte[] resultBytes = new byte[iv.length + encryptedBytes.length];
        System.arraycopy(iv, 0, resultBytes, 0, iv.length);
        System.arraycopy(encryptedBytes, 0, resultBytes, iv.length, encryptedBytes.length);

        // Base64编码
        return Base64.getEncoder().encodeToString(resultBytes);
    }

    /**
     * 对AES加密后的密文进行解密
     * @param cipherText AES加密后的密文
     * @return 解密后的明文
     */
    public static String decrypt(String cipherText,String secret) throws Exception {
        // Base64解码
        byte[] encryptedBytes = Base64.getDecoder().decode(cipherText);

        // 分离随机向量和密文
        byte[] iv = new byte[KEY_LENGTH];
        byte[] encrypted = new byte[encryptedBytes.length - KEY_LENGTH];
        System.arraycopy(encryptedBytes, 0, iv, 0, KEY_LENGTH);
        System.arraycopy(encryptedBytes, KEY_LENGTH, encrypted, 0, encrypted.length);

        // 生成密钥
        SecretKeySpec secretKeySpec = generateKey(secret);

        // 创建密码器
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new IvParameterSpec(iv));

        // 解密
        byte[] decryptedBytes = cipher.doFinal(encrypted);

        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    /**
     * 生成密钥
     * @param key 原始密钥
     * @return 密钥对象
     */
    private static SecretKeySpec generateKey(String key) throws NoSuchAlgorithmException {
        // SHA-256哈希
        MessageDigest sha256Digest = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = sha256Digest.digest(key.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }

    private static byte[] generateIV() {
        SecureRandom random = new SecureRandom();
        byte[] iv = new byte[16];
        random.nextBytes(iv);
        return iv;
    }


}
