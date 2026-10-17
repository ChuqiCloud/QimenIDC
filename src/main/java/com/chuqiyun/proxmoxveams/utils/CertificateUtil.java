package com.chuqiyun.proxmoxveams.utils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 节点公钥（PEM 证书）解析：只读公开信息，用于预览证书有效期与指纹。
 * <p>
 * 节点用的是自签证书，没有 CA 可以查，唯一能看出「证书什么时候过期、有没有被换掉」的办法
 * 就是把公钥存下来，用它算有效期和 SHA-256 指纹。这里只取第一个证书块——
 * 链式 PEM 的中间证书对判断节点身份没有意义。
 *
 * @author CodeBuddy
 * @date 2026/10/17
 */
public final class CertificateUtil {

    private static final Pattern PEM_BLOCK = Pattern.compile(
            "-----BEGIN CERTIFICATE-----(.*?)-----END CERTIFICATE-----", Pattern.DOTALL);

    private static final int MAX_INPUT_LENGTH = 16 * 1024;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private CertificateUtil() {
    }

    /**
     * 输入里是否带 PEM 证书块
     */
    public static boolean isPem(String text) {
        return text != null && PEM_BLOCK.matcher(text).find();
    }

    /**
     * 校验并归一化 PEM：去掉多余内容，只保留证书块本体，避免把注释、乱码写进库
     *
     * @param text 用户提交的公钥内容
     * @return 归一化后的 PEM
     * @throws IllegalArgumentException 内容为空或不是合法证书
     */
    public static String normalize(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        if (text.length() > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("节点公钥过长");
        }
        String pem = firstPem(text);
        if (pem == null) {
            throw new IllegalArgumentException("节点公钥必须是 PEM 格式的证书（BEGIN CERTIFICATE）");
        }
        try {
            parse(pem);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("节点公钥解析失败，无法作为 X.509 证书读取");
        }
        return pem;
    }

    /**
     * 解析 PEM 证书，返回公开信息
     *
     * @throws IllegalArgumentException 不是合法证书
     */
    public static X509Certificate parse(String text) {
        String pem = firstPem(text);
        if (pem == null) {
            throw new IllegalArgumentException("节点公钥不是 PEM 格式");
        }
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new IllegalArgumentException("节点公钥不是合法的 X.509 证书");
        }
    }

    /**
     * 证书信息：有效期、剩余天数、指纹、主体/签发者、SAN
     *
     * @param text PEM 内容，为空时返回 null
     */
    public static Map<String, Object> describe(String text) {
        if (!isPem(text)) {
            return null;
        }
        X509Certificate certificate;
        try {
            certificate = parse(text);
        } catch (IllegalArgumentException e) {
            Map<String, Object> invalid = new LinkedHashMap<>();
            invalid.put("valid", false);
            invalid.put("error", e.getMessage());
            return invalid;
        }
        long now = System.currentTimeMillis();
        Instant notBefore = certificate.getNotBefore().toInstant();
        Instant notAfter = certificate.getNotAfter().toInstant();
        long remainingDays = Math.floorDiv(notAfter.toMillis() - now, 86_400_000L);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("valid", remainingDays >= 0);
        info.put("notBefore", notBefore.toEpochMilli());
        info.put("notBeforeText", format(notBefore.toEpochMilli()));
        info.put("notAfter", notAfter.toEpochMilli());
        info.put("notAfterText", format(notAfter.toEpochMilli()));
        info.put("remainingDays", remainingDays);
        info.put("expiring", remainingDays >= 0 && remainingDays <= 30);
        info.put("fingerprint", fingerprint(certificate));
        info.put("serialNumber", certificate.getSerialNumber().toString(16).toUpperCase());
        info.put("subject", certificate.getSubjectX500Principal().getName());
        info.put("issuer", certificate.getIssuerX500Principal().getName());
        info.put("san", collectSan(certificate));
        info.put("signatureAlgorithm", certificate.getSigAlgName());
        // 自签证书 issuer 与 subject 一致，节点用自己的密钥对签自己
        info.put("selfSigned", certificate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal()));
        return info;
    }

    private static String firstPem(String text) {
        Matcher matcher = PEM_BLOCK.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return "-----BEGIN CERTIFICATE-----\n"
                + matcher.group(1).trim()
                + "\n-----END CERTIFICATE-----\n";
    }

    private static String fingerprint(X509Certificate certificate) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            StringBuilder builder = new StringBuilder();
            for (byte item : digest) {
                if (builder.length() > 0) {
                    builder.append(':');
                }
                builder.append(String.format("%02X", item));
            }
            return builder.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static List<String> collectSan(X509Certificate certificate) {
        List<String> entries = new ArrayList<>();
        try {
            if (certificate.getSubjectAlternativeNames() == null) {
                return entries;
            }
            for (List<?> item : certificate.getSubjectAlternativeNames()) {
                if (item.size() < 2 || item.get(1) == null) {
                    continue;
                }
                entries.add(String.valueOf(item.get(1)));
            }
        } catch (Exception ignored) {
            // SAN 解析失败不影响有效期展示
        }
        return entries;
    }

    private static String format(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
                .format(TIME_FORMAT);
    }
}
