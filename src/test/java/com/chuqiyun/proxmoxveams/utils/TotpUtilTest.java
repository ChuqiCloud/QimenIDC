package com.chuqiyun.proxmoxveams.utils;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpUtilTest {
    /**
     * RFC 6238 附录 B 的前三个 SHA1 测试向量
     */
    private static final String RFC_SECRET =
            Base64UrlUtil.encode("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    void matchesRfc6238Vectors() {
        assertEquals("287082", TotpUtil.generate(RFC_SECRET, 59L));
        assertEquals("081804", TotpUtil.generate(RFC_SECRET, 1111111109L));
        assertEquals("005924", TotpUtil.generate(RFC_SECRET, 1234567890L));
    }

    @Test
    void verifiesWithinDriftWindowOnly() {
        long now = 1700000000L;
        String code = TotpUtil.generate(RFC_SECRET, now);

        assertEquals(TotpUtil.currentStep(now), TotpUtil.verify(RFC_SECRET, code, now, 1));
        // 前一个时间片允许通过，再多走一步就超出容差
        assertEquals(TotpUtil.currentStep(now),
                TotpUtil.verify(RFC_SECRET, code, now + TotpUtil.DEFAULT_PERIOD, 1));
        assertEquals(-1, TotpUtil.verify(RFC_SECRET, code, now + 2L * TotpUtil.DEFAULT_PERIOD, 1));
        assertEquals(-1, TotpUtil.verify(RFC_SECRET, code, now - 2L * TotpUtil.DEFAULT_PERIOD, 1));
    }

    @Test
    void rejectsMalformedCode() {
        long now = 1700000000L;
        assertNull(TotpUtil.normalizeCode("12345"));
        assertNull(TotpUtil.normalizeCode("abcdef"));
        assertNull(TotpUtil.normalizeCode(null));
        assertEquals("123456", TotpUtil.normalizeCode(" 123 456 "));
        assertEquals(-1, TotpUtil.verify(RFC_SECRET, "abcdef", now, 1));
    }

    @Test
    void generatesDistinctSecretsAndOtpAuthUrl() {
        String first = TotpUtil.generateSecret();
        String second = TotpUtil.generateSecret();

        assertNotEquals(first, second);
        assertEquals(20, Base64UrlUtil.decode(first).length);
        String url = TotpUtil.buildOtpAuthUrl("QimenIDC", "admin", first);
        assertTrue(url.startsWith("otpauth://totp/QimenIDC:admin?secret="));
        assertTrue(url.contains("digits=6"));
        assertTrue(url.contains("period=30"));
    }
}
