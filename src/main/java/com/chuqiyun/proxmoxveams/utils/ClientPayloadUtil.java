package com.chuqiyun.proxmoxveams.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 被控端负载编解码工具。
 * <p>
 * 负载格式：magic(10字节) + iv(12字节) + ciphertext + tag(16字节)，整体Base64编码。
 * 密钥为被控端token的SHA-256摘要，AAD为 method|path|timestamp|nonce，
 * 其中 timestamp、nonce 同时作为场景参数，保证同一请求被完整复用前即失效。
 *
 * @author 星禾
 * @date 2026/10/14
 */
public class ClientPayloadUtil {
    /**
     * 负载magic，需与被控端 service/Security.py 保持一致
     */
    private static final byte[] MAGIC = "QIMENIDC-1".getBytes(StandardCharsets.UTF_8);
    private static final int MAGIC_LENGTH = MAGIC.length;
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 16;
    private static final int TAG_BITS = TAG_LENGTH * 8;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /**
     * 被控端私有明文magic，仅在加密校验关闭时使用
     */
    private static final String PLAIN_MAGIC = "QIMEN-PLAIN-1.0.";

    /**
     * 是否对被控端请求体做加密（主控与被控端版本、能力一致时才开启）
     */
    private static boolean encryptEnabled = true;

    /**
     * 是否对被控端请求参数做字段白名单过滤
     */
    private static boolean fieldWhitelistEnabled = true;

    public static boolean isEncryptEnabled() {
        return encryptEnabled;
    }

    /**
     * 根据被控端版本协商结果设置负载加密开关
     */
    public static void setEncryptEnabled(boolean enabled) {
        encryptEnabled = enabled;
    }

    public static boolean isFieldWhitelistEnabled() {
        return fieldWhitelistEnabled;
    }

    public static void setFieldWhitelistEnabled(boolean enabled) {
        fieldWhitelistEnabled = enabled;
    }

    public static byte[] buildKey(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("生成被控端负载密钥失败", e);
        }
    }

    public static byte[] buildAad(String method, String path, String timestamp, String nonce) {
        // 与被控端保持一致：缺失的参数按空值参与拼装
        String ts = timestamp == null ? "" : timestamp;
        String nc = nonce == null ? "" : nonce;
        return (method.toUpperCase() + "|" + path + "|" + ts + "|" + nc).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 加密请求体
     */
    public static String encrypt(String token, String plainText, String method, String path, String timestamp, String nonce) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(buildKey(token), "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(buildAad(method, path, timestamp, nonce));
            // GCM模式下doFinal返回的密文尾部已附带tag
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[MAGIC_LENGTH + IV_LENGTH + cipherText.length];
            System.arraycopy(MAGIC, 0, result, 0, MAGIC_LENGTH);
            System.arraycopy(iv, 0, result, MAGIC_LENGTH, IV_LENGTH);
            System.arraycopy(cipherText, 0, result, MAGIC_LENGTH + IV_LENGTH, cipherText.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new IllegalStateException("加密被控端请求负载失败", e);
        }
    }

    /**
     * 解密被控端响应体，失败返回null，由调用方按鉴权失败处理
     */
    public static String decrypt(String token, String encoded, String method, String path, String timestamp, String nonce) {
        if (encoded == null) {
            return null;
        }
        try {
            byte[] blob = Base64.getDecoder().decode(encoded);
            if (blob.length < MAGIC_LENGTH + IV_LENGTH + TAG_LENGTH) {
                return null;
            }
            for (int i = 0; i < MAGIC_LENGTH; i++) {
                if (blob[i] != MAGIC[i]) {
                    return null;
                }
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(blob, MAGIC_LENGTH, iv, 0, IV_LENGTH);
            // 被控端的tag在负载尾部，Java侧GCM要求紧跟在密文之后，这里做一次规整
            byte[] cipherText = new byte[blob.length - MAGIC_LENGTH - IV_LENGTH];
            System.arraycopy(blob, MAGIC_LENGTH + IV_LENGTH, cipherText, 0, cipherText.length);
            System.arraycopy(blob, blob.length - TAG_LENGTH, cipherText, cipherText.length - TAG_LENGTH, TAG_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(buildKey(token), "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(buildAad(method, path, timestamp, nonce));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 构造被控端可识别的明文负载，仅用于加密校验关闭的场景
     */
    public static String buildPlainPayload(JSONObject payload) {
        JSONObject wrapper = new JSONObject();
        wrapper.put("_magic", PLAIN_MAGIC);
        wrapper.put("_data", payload);
        return wrapper.toJSONString();
    }

    /**
     * 解析请求参数，供被控端做类型校验，防止参数类型被篡改绕过本地逻辑
     */
    public static String buildParamsSignature(JSONObject payload) {
        JSONObject signature = new JSONObject();
        signature.put("method", "sha256");
        signature.put("digest", base64Sha256(payload == null ? "" : JSON.toJSONString(payload)));
        return signature.toJSONString();
    }

    private static String base64Sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("生成参数摘要失败", e);
        }
    }
}
