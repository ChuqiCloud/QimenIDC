package com.chuqiyun.proxmoxveams.utils;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebAuthnUtilTest {
    private static final String RP_ID = "idc.example.com";
    private static final byte[] CREDENTIAL_ID = "credential-id-1".getBytes(StandardCharsets.US_ASCII);

    @Test
    void parsesAttestedCredentialDataAndVerifiesAssertion() throws Exception {
        KeyPair keyPair = newP256KeyPair();
        byte[] coseKey = coseP256Key((ECPublicKey) keyPair.getPublic());
        byte[] authData = concat(sha256(RP_ID), new byte[]{(byte) 0x45}, new byte[]{0, 0, 0, 0},
                new byte[16], shortBytes(CREDENTIAL_ID.length), CREDENTIAL_ID, coseKey);

        WebAuthnUtil.AuthData parsed = WebAuthnUtil.parseAuthData(authData);
        assertArrayEquals(CREDENTIAL_ID, parsed.getCredentialId());
        assertEquals(WebAuthnUtil.ALG_ES256, parsed.getAlgorithm());
        // 0x45 = UP | UV | AT
        assertTrue(parsed.isUserPresent());
        assertTrue(parsed.isUserVerified());
        assertTrue(parsed.hasAttestedCredentialData());
        assertEquals(0L, parsed.getSignCount());
        // 重新解析出来的公钥必须能验签（证明 COSE 解析正确）
        assertTrue(verifySignature(parsed, keyPair.getPrivate(), "assertion-payload"));
    }

    @Test
    void extractsAuthDataFromAttestationObject() throws Exception {
        KeyPair keyPair = newP256KeyPair();
        byte[] coseKey = coseP256Key((ECPublicKey) keyPair.getPublic());
        byte[] authData = concat(sha256(RP_ID), new byte[]{(byte) 0x41}, new byte[]{0, 0, 0, 0},
                new byte[16], shortBytes(CREDENTIAL_ID.length), CREDENTIAL_ID, coseKey);
        byte[] attestationObject = concat(new byte[]{(byte) 0xA3},
                cborText("fmt"), cborText("none"),
                cborText("attStmt"), new byte[]{(byte) 0xA0},
                cborText("authData"), cborBytes(authData));

        byte[] extracted = WebAuthnUtil.extractAuthData(attestationObject);
        assertArrayEquals(authData, extracted);
    }

    @Test
    void rejectsWrongRpIdAndTamperedSignature() throws Exception {
        KeyPair keyPair = newP256KeyPair();
        byte[] coseKey = coseP256Key((ECPublicKey) keyPair.getPublic());
        WebAuthnUtil.AuthData authData = WebAuthnUtil.parseAuthData(concat(sha256(RP_ID),
                new byte[]{0x41}, new byte[]{0, 0, 0, 0}, new byte[16],
                shortBytes(CREDENTIAL_ID.length), CREDENTIAL_ID, coseKey));

        assertThrows(IllegalArgumentException.class,
                () -> WebAuthnUtil.verifyRpId(authData.getRpIdHash(), "evil.example.com"));

        byte[] clientDataHash = sha256("client-data".getBytes(StandardCharsets.US_ASCII));
        byte[] authenticatorData = concat(sha256(RP_ID), new byte[]{0x01}, new byte[]{0, 0, 0, 2});
        byte[] signature = sign(keyPair, concat(authenticatorData, clientDataHash));
        assertTrue(WebAuthnUtil.verifySignature(WebAuthnUtil.ALG_ES256, coseKey,
                authenticatorData, clientDataHash, signature));
        assertFalse(WebAuthnUtil.verifySignature(WebAuthnUtil.ALG_ES256, coseKey,
                authenticatorData, sha256("other".getBytes(StandardCharsets.US_ASCII)), signature));
    }

    @Test
    void parsesClientDataJson() {
        byte[] json = ("{\"type\":\"webauthn.get\",\"challenge\":\"abc-_123\","
                + "\"origin\":\"https://idc.example.com\"}").getBytes(StandardCharsets.UTF_8);
        WebAuthnUtil.ClientData clientData = WebAuthnUtil.parseClientData(json, "webauthn.get");

        assertEquals("abc-_123", clientData.getChallenge());
        assertArrayEquals(sha256(json), clientData.getHash());
        assertThrows(IllegalArgumentException.class,
                () -> WebAuthnUtil.parseClientData(json, "webauthn.create"));
    }

    @Test
    void parsesRsaCoseModulusAndExponentInStandardOrder() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        java.security.interfaces.RSAPublicKey publicKey = (java.security.interfaces.RSAPublicKey) pair.getPublic();
        byte[] modulus = unsigned(publicKey.getModulus().toByteArray());
        byte[] exponent = unsigned(publicKey.getPublicExponent().toByteArray());
        byte[] cose = concat(new byte[]{(byte) 0xA4},
                new byte[]{0x01, 0x03},
                new byte[]{0x03, 0x39, 0x01, 0x00},
                new byte[]{0x20}, cborBytes(modulus),
                new byte[]{0x21}, cborBytes(exponent));
        byte[] authData = concat(sha256(RP_ID), new byte[]{0x01}, new byte[]{0, 0, 0, 0});
        byte[] clientDataHash = sha256("rsa-assertion".getBytes(StandardCharsets.US_ASCII));
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.getPrivate());
        signer.update(authData);
        signer.update(clientDataHash);

        assertTrue(WebAuthnUtil.verifySignature(WebAuthnUtil.ALG_RS256, cose,
                authData, clientDataHash, signer.sign()));
    }

    private boolean verifySignature(WebAuthnUtil.AuthData parsed, java.security.PrivateKey privateKey,
                                    String payload) throws Exception {
        byte[] authenticatorData = concat(sha256(RP_ID), new byte[]{0x01}, new byte[]{0, 0, 0, 2});
        byte[] clientDataHash = sha256(payload.getBytes(StandardCharsets.UTF_8));
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(privateKey);
        signer.update(concat(authenticatorData, clientDataHash));
        byte[] signature = signer.sign();
        return WebAuthnUtil.verifySignature(parsed.getAlgorithm(), parsed.getCredentialPublicKey(),
                authenticatorData, clientDataHash, signature);
    }

    private byte[] sign(KeyPair keyPair, byte[] data) throws Exception {
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(data);
        return signer.sign();
    }

    private static KeyPair newP256KeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    /**
     * 手工编码 COSE_Key：{1: 2, 3: -7, -1: 1, -2: x, -3: y}
     */
    private static byte[] coseP256Key(ECPublicKey publicKey) {
        byte[] x = toFixedLength(publicKey.getW().getAffineX().toByteArray(), 32);
        byte[] y = toFixedLength(publicKey.getW().getAffineY().toByteArray(), 32);
        return concat(new byte[]{(byte) 0xA5},
                new byte[]{0x01, 0x02},
                new byte[]{0x03, 0x26},
                new byte[]{0x20, 0x01},
                new byte[]{0x21}, cborBytes(x),
                new byte[]{0x22}, cborBytes(y));
    }

    private static byte[] toFixedLength(byte[] value, int length) {
        if (value.length == length) {
            return value;
        }
        byte[] fixed = new byte[length];
        int copyLength = Math.min(value.length, length);
        System.arraycopy(value, value.length - copyLength, fixed, length - copyLength, copyLength);
        return fixed;
    }

    private static byte[] unsigned(byte[] value) {
        return value.length > 1 && value[0] == 0 ? Arrays.copyOfRange(value, 1, value.length) : value;
    }

    private static byte[] cborText(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return concat(smallHeader(0x60, bytes.length), bytes);
    }

    private static byte[] cborBytes(byte[] value) {
        return concat(smallHeader(0x40, value.length), value);
    }

    private static byte[] smallHeader(int major, int length) {
        if (length < 24) {
            return new byte[]{(byte) (major | length)};
        }
        if (length < 256) {
            return new byte[]{(byte) (major | 24), (byte) length};
        }
        return new byte[]{(byte) (major | 25), (byte) (length >> 8), (byte) length};
    }

    private static byte[] shortBytes(int value) {
        return new byte[]{(byte) (value >> 8), (byte) value};
    }

    private static byte[] sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha256(byte[] value) {
        return WebAuthnUtil.sha256(value);
    }

    private static byte[] concat(byte[]... arrays) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Arrays.stream(arrays).forEach(output::writeBytes);
        return output.toByteArray();
    }
}
