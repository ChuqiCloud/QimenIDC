package com.chuqiyun.proxmoxveams.utils;

import java.util.Base64;

/**
 * @Author: 星禾
 * @Description: WebAuthn 等场景使用的无填充 base64url 编码
 * @DateTime: 2026/10/17 15:10
 */
public final class Base64UrlUtil {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private Base64UrlUtil() {
    }

    public static String encode(byte[] data) {
        return data == null ? null : ENCODER.encodeToString(data);
    }

    /**
     * 兼容带填充与非填充两种输入，非法输入抛出 IllegalArgumentException
     */
    public static byte[] decode(String data) {
        if (data == null) {
            return null;
        }
        return DECODER.decode(data);
    }
}
