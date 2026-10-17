package com.chuqiyun.proxmoxveams.utils;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;

/**
 * @Author: 星禾
 * @Description: WebAuthn 相关的二进制编解码与签名校验
 * @DateTime: 2026/10/17 15:50
 *
 * 只实现注册与认证所必需的子集：COSE 公钥解析、authData 解析、
 * clientDataJSON 校验以及 ES256 / RS256 签名验证。
 */
public final class WebAuthnUtil {
    public static final int ALG_ES256 = -7;
    public static final int ALG_RS256 = -257;
    private static final int FLAG_USER_PRESENT = 0x01;
    private static final int FLAG_USER_VERIFIED = 0x04;
    private static final int FLAG_ATTESTED_CREDENTIAL_DATA = 0x40;
    private static final int FLAG_BACKUP_ELIGIBLE = 0x08;
    private static final int FLAG_BACKUP_STATE = 0x10;

    private WebAuthnUtil() {
    }

    /**
     * 解析后的认证器数据
     */
    public static final class AuthData {
        private final byte[] rpIdHash;
        private final int flags;
        private final long signCount;
        private final byte[] aaguid;
        private final byte[] credentialId;
        private final byte[] credentialPublicKey;
        private final int algorithm;

        private AuthData(byte[] rpIdHash, int flags, long signCount, byte[] aaguid,
                         byte[] credentialId, byte[] credentialPublicKey, int algorithm) {
            this.rpIdHash = rpIdHash;
            this.flags = flags;
            this.signCount = signCount;
            this.aaguid = aaguid;
            this.credentialId = credentialId;
            this.credentialPublicKey = credentialPublicKey;
            this.algorithm = algorithm;
        }

        public byte[] getRpIdHash() {
            return rpIdHash;
        }

        public long getSignCount() {
            return signCount;
        }

        public byte[] getAaguid() {
            return aaguid;
        }

        public byte[] getCredentialId() {
            return credentialId;
        }

        public byte[] getCredentialPublicKey() {
            return credentialPublicKey;
        }

        public int getAlgorithm() {
            return algorithm;
        }

        public boolean isUserVerified() {
            return (flags & FLAG_USER_VERIFIED) != 0;
        }

        public boolean isUserPresent() {
            return (flags & FLAG_USER_PRESENT) != 0;
        }

        public boolean isBackupEligible() {
            return (flags & FLAG_BACKUP_ELIGIBLE) != 0;
        }

        public boolean isBackupState() {
            return (flags & FLAG_BACKUP_STATE) != 0;
        }

        public boolean hasAttestedCredentialData() {
            return (flags & FLAG_ATTESTED_CREDENTIAL_DATA) != 0;
        }
    }

    /**
     * 解析认证器数据（注册响应中的 attestationObject 内 authData，或认证响应中的 authenticatorData）
     */
    public static AuthData parseAuthData(byte[] authData) {
        if (authData == null || authData.length < 37) {
            throw new IllegalArgumentException("认证器数据长度不足");
        }
        ByteBuffer buffer = ByteBuffer.wrap(authData);
        byte[] rpIdHash = new byte[32];
        buffer.get(rpIdHash);
        int flags = buffer.get() & 0xFF;
        long signCount = Integer.toUnsignedLong(buffer.getInt());
        byte[] aaguid = new byte[0];
        byte[] credentialId = new byte[0];
        byte[] credentialPublicKey = new byte[0];
        int algorithm = 0;
        if ((flags & FLAG_ATTESTED_CREDENTIAL_DATA) != 0) {
            if (buffer.remaining() < 18) {
                throw new IllegalArgumentException("凭据数据长度不足");
            }
            aaguid = new byte[16];
            buffer.get(aaguid);
            int credentialIdLength = Short.toUnsignedInt(buffer.getShort());
            if (credentialIdLength <= 0 || buffer.remaining() < credentialIdLength) {
                throw new IllegalArgumentException("凭据ID长度非法");
            }
            credentialId = new byte[credentialIdLength];
            buffer.get(credentialId);
            CoseKey coseKey = CoseKey.parse(Arrays.copyOfRange(authData, buffer.position(), authData.length));
            algorithm = coseKey.algorithm;
            credentialPublicKey = coseKey.raw;
        }
        return new AuthData(rpIdHash, flags, signCount, aaguid, credentialId, credentialPublicKey, algorithm);
    }

    /**
     * 从 attestationObject（CBOR）中取出 authData
     */
    public static byte[] extractAuthData(byte[] attestationObject) {
        CborReader reader = new CborReader(attestationObject);
        int mapSize = reader.readMapHeader();
        byte[] authData = null;
        for (int i = 0; i < mapSize; i++) {
            String key = reader.readText();
            if ("authData".equals(key)) {
                authData = reader.readBytes();
            } else {
                reader.skipItem();
            }
        }
        if (authData == null) {
            throw new IllegalArgumentException("attestationObject 缺少 authData");
        }
        return authData;
    }

    /**
     * 校验 clientDataJSON 的类型，并返回挑战原文与 hash
     */
    public static ClientData parseClientData(byte[] clientDataJson, String expectedType) {
        if (clientDataJson == null || clientDataJson.length == 0) {
            throw new IllegalArgumentException("clientDataJSON 为空");
        }
        ClientData clientData = new ClientData();
        String json = new String(clientDataJson, StandardCharsets.UTF_8);
        clientData.type = JsonFieldReader.readString(json, "type");
        clientData.challenge = JsonFieldReader.readString(json, "challenge");
        if (!expectedType.equals(clientData.type)) {
            throw new IllegalArgumentException("clientData type 不匹配");
        }
        clientData.hash = sha256(clientDataJson);
        return clientData;
    }

    public static final class ClientData {
        private String type;
        private String challenge;
        private byte[] hash;

        public String getChallenge() {
            return challenge;
        }

        public byte[] getHash() {
            return hash;
        }
    }

    private static void requireP256(int crv) {
        if (crv != 1) {
            throw new IllegalArgumentException("仅支持 P-256 曲线");
        }
    }

    /**
     * 校验 rpIdHash
     */
    public static void verifyRpId(byte[] rpIdHash, String rpId) {
        byte[] expected = sha256(rpId.getBytes(StandardCharsets.UTF_8));
        if (!MessageDigest.isEqual(expected, rpIdHash)) {
            throw new IllegalArgumentException("rpId 校验失败");
        }
    }

    /**
     * 校验断言签名
     */
    public static boolean verifySignature(int algorithm, byte[] cosePublicKey,
                                          byte[] authenticatorData, byte[] clientDataHash,
                                          byte[] signature) {
        try {
            PublicKey publicKey = toPublicKey(algorithm, cosePublicKey);
            String signatureAlgorithm = algorithm == ALG_ES256 ? "SHA256withECDSA" : "SHA256withRSA";
            Signature verifier = Signature.getInstance(signatureAlgorithm);
            verifier.initVerify(publicKey);
            verifier.update(authenticatorData);
            verifier.update(clientDataHash);
            return verifier.verify(signature);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    /**
     * COSE 公钥转 JDK 公钥
     */
    public static PublicKey toPublicKey(int algorithm, byte[] cosePublicKey) throws GeneralSecurityException {
        CoseKey coseKey = CoseKey.parse(cosePublicKey);
        if (algorithm == ALG_ES256) {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec ecParameterSpec = parameters.getParameterSpec(ECParameterSpec.class);
            ECPoint point = new ECPoint(new BigInteger(1, coseKey.x), new BigInteger(1, coseKey.y));
            return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, ecParameterSpec));
        }
        if (algorithm == ALG_RS256) {
            return KeyFactory.getInstance("RSA").generatePublic(
                    new RSAPublicKeySpec(new BigInteger(1, coseKey.x), new BigInteger(1, coseKey.y)));
        }
        throw new GeneralSecurityException("不支持的签名算法");
    }

    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * COSE_Key 子集：仅取 kty / alg / crv / x / y / n / e
     */
    private static final class CoseKey {
        private final int algorithm;
        private final byte[] x;
        private final byte[] y;
        private final byte[] raw;

        private CoseKey(int algorithm, byte[] x, byte[] y, byte[] raw) {
            this.algorithm = algorithm;
            this.x = x;
            this.y = y;
            this.raw = raw;
        }

        private static CoseKey parse(byte[] coseBytes) {
            CborReader reader = new CborReader(coseBytes);
            int mapSize = reader.readMapHeader();
            int kty = 0;
            int algorithm = 0;
            int crv = 0;
            byte[] minusOneBytes = null;
            byte[] minusTwoBytes = null;
            byte[] minusThreeBytes = null;
            for (int i = 0; i < mapSize; i++) {
                int label = reader.readInt();
                switch (label) {
                    case 1 -> kty = reader.readInt();
                    case 3 -> algorithm = reader.readInt();
                    case -1 -> {
                        if (reader.peekMajorType() == 0x40) {
                            minusOneBytes = reader.readBytes(); // RSA 的 n
                        } else {
                            crv = reader.readInt(); // EC 的 crv，CBOR 字段可按任意顺序出现
                        }
                    }
                    case -2 -> minusTwoBytes = reader.readBytes();
                    case -3 -> minusThreeBytes = reader.readBytes();
                    default -> reader.skipItem();
                }
            }
            byte[] x;
            byte[] y;
            if (kty == 2 && algorithm == ALG_ES256) {
                requireP256(crv);
                x = minusTwoBytes;
                y = minusThreeBytes;
            } else if (kty == 3 && algorithm == ALG_RS256) {
                x = minusOneBytes; // RSA 的 n
                y = minusTwoBytes; // RSA 的 e
            } else {
                throw new IllegalArgumentException("不支持的 COSE 公钥类型或算法");
            }
            if (x == null || y == null) {
                throw new IllegalArgumentException("COSE 公钥缺少坐标");
            }
            if (algorithm != ALG_ES256 && algorithm != ALG_RS256) {
                throw new IllegalArgumentException("不支持的 COSE 算法: " + algorithm);
            }
            return new CoseKey(algorithm, x, y, coseBytes);
        }
    }

    /**
     * 极简 CBOR 读取器，仅支持注册响应解析所需的类型
     */
    private static final class CborReader {
        private final byte[] data;
        private int position;

        private CborReader(byte[] data) {
            this.data = data;
            this.position = 0;
        }

        private int peekMajorType() {
            if (position >= data.length) {
                throw new IllegalArgumentException("CBOR 数据不足");
            }
            return data[position] & 0xE0;
        }

        private int readMapHeader() {
            int initial = readInitial();
            if ((initial & 0xE0) != 0xA0) {
                throw new IllegalArgumentException("期望 CBOR map");
            }
            return readLength(initial & 0x1F);
        }

        private int readInt() {
            int initial = readInitial();
            int majorType = initial & 0xE0;
            int value = readLength(initial & 0x1F);
            if (majorType == 0x20) {
                return -1 - value;
            }
            if (majorType == 0x00) {
                return value;
            }
            throw new IllegalArgumentException("期望 CBOR 整数");
        }

        private String readText() {
            int initial = readInitial();
            if ((initial & 0xE0) != 0x60) {
                throw new IllegalArgumentException("期望 CBOR 文本");
            }
            int length = readLength(initial & 0x1F);
            ensure(length);
            String value = new String(data, position, length, StandardCharsets.UTF_8);
            position += length;
            return value;
        }

        private byte[] readBytes() {
            int initial = readInitial();
            if ((initial & 0xE0) != 0x40) {
                throw new IllegalArgumentException("期望 CBOR 字节串");
            }
            int length = readLength(initial & 0x1F);
            ensure(length);
            byte[] value = Arrays.copyOfRange(data, position, position + length);
            position += length;
            return value;
        }

        private void skipItem() {
            int initial = readInitial();
            int majorType = initial & 0xE0;
            int additional = initial & 0x1F;
            switch (majorType) {
                case 0x00, 0x20 -> readLength(additional);
                case 0x40, 0x60 -> {
                    int length = readLength(additional);
                    ensure(length);
                    position += length;
                }
                case 0x80 -> {
                    int length = readLength(additional);
                    for (int i = 0; i < length; i++) {
                        skipItem();
                    }
                }
                case 0xA0 -> {
                    int length = readLength(additional);
                    for (int i = 0; i < length * 2; i++) {
                        skipItem();
                    }
                }
                default -> throw new IllegalArgumentException("不支持的 CBOR 类型");
            }
        }

        private int readInitial() {
            ensure(1);
            return data[position++] & 0xFF;
        }

        private int readLength(int additional) {
            switch (additional) {
                case 24 -> {
                    ensure(1);
                    return data[position++] & 0xFF;
                }
                case 25 -> {
                    ensure(2);
                    int value = ((data[position] & 0xFF) << 8) | (data[position + 1] & 0xFF);
                    position += 2;
                    return value;
                }
                case 26 -> {
                    ensure(4);
                    int value = ByteBuffer.wrap(data, position, 4).getInt();
                    position += 4;
                    return value;
                }
                default -> {
                    return additional;
                }
            }
        }

        private void ensure(int length) {
            if (position + length > data.length) {
                throw new IllegalArgumentException("CBOR 数据不完整");
            }
        }
    }

    /**
     * 从 JSON 中取字符串字段，避免为几个字段引入额外解析层
     */
    private static final class JsonFieldReader {
        private static String readString(String json, String field) {
            String pattern = "\"" + field + "\"";
            int index = json.indexOf(pattern);
            if (index < 0) {
                throw new IllegalArgumentException("clientDataJSON 缺少字段: " + field);
            }
            int colon = json.indexOf(':', index + pattern.length());
            if (colon < 0) {
                throw new IllegalArgumentException("clientDataJSON 格式非法");
            }
            int quoteStart = json.indexOf('"', colon);
            if (quoteStart < 0) {
                throw new IllegalArgumentException("clientDataJSON 字段格式非法");
            }
            StringBuilder builder = new StringBuilder();
            for (int i = quoteStart + 1; i < json.length(); i++) {
                char current = json.charAt(i);
                if (current == '\\') {
                    i++;
                    if (i < json.length()) {
                        builder.append(json.charAt(i));
                    }
                } else if (current == '"') {
                    return builder.toString();
                } else {
                    builder.append(current);
                }
            }
            throw new IllegalArgumentException("clientDataJSON 字段未闭合");
        }
    }

    /**
     * 拼接字节数组
     */
    public static byte[] concat(byte[]... arrays) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] array : arrays) {
            if (array != null) {
                output.writeBytes(array);
            }
        }
        return output.toByteArray();
    }
}
