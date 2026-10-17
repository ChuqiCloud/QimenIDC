package com.chuqiyun.proxmoxveams.utils;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import sun.security.x509.AlgorithmId;
import sun.security.x509.CertificateAlgorithmId;
import sun.security.x509.CertificateSerialNumber;
import sun.security.x509.CertificateValidity;
import sun.security.x509.CertificateX509Key;
import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CertificateUtilTest {

    private static String pem(long notAfterDays) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        java.security.KeyPair pair = generator.generateKeyPair();
        X509CertInfo info = new X509CertInfo();
        info.set(X509CertInfo.VERSION, new sun.security.x509.CertificateVersion(
                sun.security.x509.CertificateVersion.V3));
        info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(new BigInteger("4096")));
        info.set(X509CertInfo.SUBJECT, new X500Name("CN=QimenIDC node"));
        info.set(X509CertInfo.ISSUER, new X500Name("CN=QimenIDC node"));
        Date from = Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
        Date to = Date.from(Instant.now().plus(notAfterDays, ChronoUnit.DAYS));
        info.set(X509CertInfo.VALIDITY, new CertificateValidity(from, to));
        info.set(X509CertInfo.KEY, new CertificateX509Key(pair.getPublic()));
        info.set(X509CertInfo.ALGORITHM_ID,
                new CertificateAlgorithmId(AlgorithmId.get("SHA256withRSA")));
        X509CertImpl certificate = new X509CertImpl(info);
        certificate.sign(pair.getPrivate(), "SHA256withRSA");
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
