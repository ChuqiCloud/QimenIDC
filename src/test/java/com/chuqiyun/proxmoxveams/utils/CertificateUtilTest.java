package com.chuqiyun.proxmoxveams.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CertificateUtilTest {

    @TempDir
    Path temporaryDirectory;

    private String pem(long notAfterDays) throws Exception {
        Path store = temporaryDirectory.resolve(java.util.UUID.randomUUID() + ".p12");
        String executable = System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool";
        // 使用 JDK 公共工具生成证书，避免依赖随 JDK 版本变化的内部 API。
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-genkeypair", "-alias", "test", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=QimenIDC node", "-startdate", "-10d",
                "-validity", Long.toString(notAfterDays + 10), "-storetype", "PKCS12",
                "-keystore", store.toString(), "-storepass", "changeit", "-noprompt")
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(store)) {
            keyStore.load(input, "changeit".toCharArray());
        }
        X509Certificate certificate = (X509Certificate) keyStore.getCertificate("test");
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(certificate.getEncoded());
        return "-----BEGIN CERTIFICATE-----\n" + body + "\n-----END CERTIFICATE-----\n";
    }

    @Test
    void validPemExposesRemainingDaysAndFingerprint() throws Exception {
        Map<String, Object> description = CertificateUtil.describe(pem(365));

        assertNotNull(description);
        assertEquals(Boolean.TRUE, description.get("valid"));
        assertEquals(Boolean.FALSE, description.get("expiring"));
        long remainingDays = (Long) description.get("remainingDays");
        assertTrue(remainingDays >= 360 && remainingDays <= 366, "unexpected remaining days: " + remainingDays);
        assertTrue(((String) description.get("fingerprint")).matches("([0-9A-F]{2}:){31}[0-9A-F]{2}"));
        assertEquals(Boolean.TRUE, description.get("selfSigned"));
        assertNotNull(description.get("notAfterText"));
    }

    @Test
    void nearlyExpiredCertificateIsFlagged() throws Exception {
        Map<String, Object> description = CertificateUtil.describe(pem(10));

        assertEquals(Boolean.TRUE, description.get("valid"));
        assertEquals(Boolean.TRUE, description.get("expiring"));
    }

    @Test
    void expiredCertificateIsReportedAsInvalid() throws Exception {
        Map<String, Object> description = CertificateUtil.describe(pem(-5));

        assertEquals(Boolean.FALSE, description.get("valid"));
        assertTrue((Long) description.get("remainingDays") < 0);
    }

    @Test
    void plainTextIsNotAccepted() {
        assertNull(CertificateUtil.describe("ssh-ed25519 AAAAC3Nza node"));
        assertNull(CertificateUtil.describe(null));
        assertFalse(CertificateUtil.isPem("203.0.113.7"));
        assertThrows(IllegalArgumentException.class, () -> CertificateUtil.normalize("not a pem"));
    }

    @Test
    void normalizeKeepsOnlyTheFirstCertificateBlock() throws Exception {
        String first = pem(30);
        String second = pem(60);
        String dirty = "节点公钥如下：\n" + first + "\n第二张\n" + second;

        String normalized = CertificateUtil.normalize(dirty);
        assertNotNull(normalized);
        assertTrue(normalized.startsWith("-----BEGIN CERTIFICATE-----\n"));
        assertTrue(normalized.endsWith("-----END CERTIFICATE-----\n"));
        assertEquals(1, normalized.split("BEGIN CERTIFICATE").length - 1);
        assertEquals(CertificateUtil.describe(first).get("fingerprint"),
                CertificateUtil.describe(normalized).get("fingerprint"));
    }

    @Test
    void emptyPublicKeyMeansUnbind() {
        assertNull(CertificateUtil.normalize(""));
        assertNull(CertificateUtil.normalize("   "));
    }

    @Test
    void certificateReadsBackThroughJdkFactory() throws Exception {
        String pem = pem(1);
        X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
        assertTrue(certificate.getNotAfter().toInstant().isAfter(Instant.now()));
    }
}
