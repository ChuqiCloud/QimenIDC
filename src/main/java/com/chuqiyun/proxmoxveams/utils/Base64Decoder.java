package com.chuqiyun.proxmoxveams.utils;

import java.util.Base64;

/**
 * @author mryunqi
 * @date 2023/5/8
 */
public class Base64Decoder {
    /**
     * base64解码
     * @param encodedString
     * @return
     */
    public static String decodeBase64(String encodedString) {
        byte[] decodedBytes = Base64.getDecoder().decode(encodedString);
        return new String(decodedBytes);
    }
}
