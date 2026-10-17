package com.chuqiyun.proxmoxveams.utils;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 用真实证书文件校验解析：证书头、换行、缩进都不可控，前面自签构造的用例覆盖不到。
 * 证书由测试现场用 openssl 生成，缺失时跳过。
 */
class CertificateUtilProbeTest {

    private static String openssl() throws IOException, InterruptedException {
        Path key = Files.createTempFile("cert-test", ".key");
        Path crt = Files.createTempFile("cert-test", ".crt");
        try {
            Process process = new ProcessBuilder("openssl", "req", "-x509", "-nodes", "-days", "120",
                    "-newkey", "rsa:2048", "-subj", "/CN=QimenIDC node", "-addext",
                    "subjectAltName=IP:127.0.0.1,DNS:node.example.com",
                    "-keyout", key.toString(), "-out", crt.toString())
                    .redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            if (process.waitFor() != 0) {
                return null;
            }
            return new String(Files.readAllBytes(crt), StandardCharsets.UTF_8);
        } finally {
            Files.deleteIfExists(key);
            Files.deleteIfExists(crt);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void opensslCertificateIsParsedWithSanAndSerial() throws Exception {
        String pem = openssl();
        if (pem == null) {
            return;
        }
        Map<String, Object> description = CertificateUtil.describe(pem);
        assertNotNull(description);
        assertEquals(Boolean.TRUE, description.get("valid"));
        assertFalse(description.containsKey("error"));
        long remainingDays = (Long) description.get("remainingDays");
        assertEquals(119L, remainingDays, 1L);
        assertEquals(Boolean.TRUE, description.get("selfSigned"));
        assertEquals("127.0.0.1,node.example.com", String.join(",", (Iterable<String>) description.get("san")));
        assertNotNull(description.get("serialNumber"));

        // 展开的 PEM 中间被插了空行或首尾空格也能归一化
        String messy = "\n  " + pem.replace("\n", "\n  ") + "  \n";
        assertEquals(description.get("fingerprint"), CertificateUtil.describe(CertificateUtil.normalize(messy)).get("fingerprint"));
    }
}
