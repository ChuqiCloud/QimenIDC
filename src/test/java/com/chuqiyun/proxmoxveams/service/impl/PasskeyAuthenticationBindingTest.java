package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.common.AuthException;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.entity.SysPasskey;
import com.chuqiyun.proxmoxveams.entity.Sysuser;
import com.chuqiyun.proxmoxveams.service.SysPasskeyService;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.utils.Base64UrlUtil;
import com.chuqiyun.proxmoxveams.utils.WebAuthnUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: 星禾
 * @Description: 校验有用户名挑战和无用户名认证之间的账号绑定，使用真实签名覆盖认证链路
 * @DateTime: 2026/10/17 16:20
 */
class PasskeyAuthenticationBindingTest {
    private static final String RP_ID = "idc.example.com";
    private PasskeyServiceImpl service;
    private SysPasskeyService credentials;
    private Sysuser accountA;
    private Sysuser accountB;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        service = new PasskeyServiceImpl();
        credentials = mock(SysPasskeyService.class);
        SysuserService users = mock(SysuserService.class);
        ReflectionTestUtils.setField(service, "sysPasskeyService", credentials);
        ReflectionTestUtils.setField(service, "sysuserService", users);
        ReflectionTestUtils.setField(service, "rpId", RP_ID);
        ReflectionTestUtils.setField(service, "origin", "https://" + RP_ID);
        accountA = account(1, "account-a");
        accountB = account(2, "account-b");
        when(users.getById(1)).thenReturn(accountA);
        when(users.getById(2)).thenReturn(accountB);
        when(credentials.listByUserId(1)).thenReturn(List.of());
        when(credentials.listByUserId(2)).thenReturn(List.of());
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        addCredential(accountA);
        addCredential(accountB);
    }

    @Test
    void namedChallengeCannotSwitchAccountsByOmittingUsernameOnFinish() throws Exception {
        String challenge = (String) service.createAuthenticationOptions(accountA).get("challenge");

        AuthException error = assertThrows(AuthException.class,
                () -> service.finishAuthentication(assertion(challenge, accountB), null));

        assertEquals(ResponseResult.RespCode.PASSKEY_FAIL, error.getRespCode());
        verify(credentials, never()).touchLastUsed(any(), anyLong(), anyLong());
    }

    @Test
    void namedChallengeMayFinishWithoutUsernameForItsOriginalAccount() throws Exception {
        String challenge = (String) service.createAuthenticationOptions(accountA).get("challenge");

        assertSame(accountA, service.finishAuthentication(assertion(challenge, accountA), null));
        assertThrows(AuthException.class,
                () -> service.finishAuthentication(assertion(challenge, accountA), null));
    }

    @Test
    void discoverableChallengeStillResolvesCredentialOwner() throws Exception {
        String challenge = (String) service.createAuthenticationOptions(null).get("challenge");

        assertSame(accountB, service.finishAuthentication(assertion(challenge, accountB), null));
    }

    @Test
    void explicitFinishAccountMustMatchNamedChallenge() throws Exception {
        String challenge = (String) service.createAuthenticationOptions(accountA).get("challenge");

        assertThrows(AuthException.class,
                () -> service.finishAuthentication(assertion(challenge, accountB), accountB.getUuid()));
        verify(credentials, never()).touchLastUsed(any(), anyLong(), anyLong());
    }

    private void addCredential(Sysuser account) {
        SysPasskey credential = new SysPasskey();
        credential.setUserid(account.getId());
        credential.setCredentialId(credentialId(account));
        credential.setAlgorithm(WebAuthnUtil.ALG_RS256);
        credential.setSignCount(0L);
        RSAPublicKey key = (RSAPublicKey) keyPair.getPublic();
        credential.setPublicKey(Base64UrlUtil.encode(concat(new byte[]{(byte) 0xa4, 1, 3, 3, 0x39, 1, 0, 0x20},
                cborBytes(unsigned(key.getModulus().toByteArray())), new byte[]{0x21},
                cborBytes(unsigned(key.getPublicExponent().toByteArray())))));
        when(credentials.getByCredentialId(credential.getCredentialId())).thenReturn(credential);
    }

    private JSONObject assertion(String challenge, Sysuser account) throws Exception {
        byte[] authData = concat(WebAuthnUtil.sha256(RP_ID.getBytes(StandardCharsets.UTF_8)),
                new byte[]{1, 0, 0, 0, 1});
        JSONObject clientData = new JSONObject();
        clientData.put("type", "webauthn.get");
        clientData.put("challenge", challenge);
        clientData.put("origin", "https://" + RP_ID);
        byte[] clientJson = clientData.toJSONString().getBytes(StandardCharsets.UTF_8);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(authData);
        signature.update(WebAuthnUtil.sha256(clientJson));
        JSONObject result = new JSONObject();
        result.put("rawId", credentialId(account));
        result.put("authenticatorData", Base64UrlUtil.encode(authData));
        result.put("clientDataJSON", Base64UrlUtil.encode(clientJson));
        result.put("signature", Base64UrlUtil.encode(signature.sign()));
        return result;
    }

    private static Sysuser account(int id, String uuid) {
        Sysuser account = new Sysuser();
        account.setId(id);
        account.setUuid(uuid);
        return account;
    }

    private static String credentialId(Sysuser account) {
        return Base64UrlUtil.encode(("credential-" + account.getUuid()).getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] unsigned(byte[] value) {
        return value[0] == 0 ? Arrays.copyOfRange(value, 1, value.length) : value;
    }

    private static byte[] cborBytes(byte[] value) {
        byte[] header = value.length < 24 ? new byte[]{(byte) (0x40 | value.length)}
                : value.length < 256 ? new byte[]{0x58, (byte) value.length}
                : new byte[]{0x59, (byte) (value.length >> 8), (byte) value.length};
        return concat(header, value);
    }

    private static byte[] concat(byte[]... values) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        Arrays.stream(values).forEach(result::writeBytes);
        return result.toByteArray();
    }
}
