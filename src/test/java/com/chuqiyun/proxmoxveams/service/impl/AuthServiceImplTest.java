package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.dto.MfaChallenge;
import com.chuqiyun.proxmoxveams.entity.Sysuser;
import com.chuqiyun.proxmoxveams.security.MfaTicketService;
import com.chuqiyun.proxmoxveams.service.PasskeyService;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.utils.JWTUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: 星禾
 * @Description: Passkey discoverable login and session token binding regressions
 * @DateTime: 2026/10/17 16:01
 */
class AuthServiceImplTest {
    private static final String SECRET = "test-session-secret";

    private AuthServiceImpl service;
    private PasskeyService passkeyService;
    private SysuserService sysuserService;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl();
        passkeyService = mock(PasskeyService.class);
        sysuserService = mock(SysuserService.class);
        MfaTicketService tickets = mock(MfaTicketService.class);
        when(tickets.getTicketExpireMillis()).thenReturn(300_000L);
        ReflectionTestUtils.setField(service, "secret", SECRET);
        ReflectionTestUtils.setField(service, "passkeyService", passkeyService);
        ReflectionTestUtils.setField(service, "sysuserService", sysuserService);
        ReflectionTestUtils.setField(service, "mfaTicketService", tickets);
    }

    @Test
    void directChallengeWithoutUsernameAllowsDiscoverableCredentials() {
        when(passkeyService.createAuthenticationOptions(null)).thenReturn(Map.of("challenge", "challenge"));

        MfaChallenge challenge = service.passkeyDirectChallenge(null);

        assertNotNull(challenge);
        assertEquals("challenge", ((Map<?, ?>) challenge.getPublicKeyOptions()).get("challenge"));
        verify(passkeyService).createAuthenticationOptions(null);
    }

    @Test
    void directDiscoverableLoginUsesDatabaseSessionMarkerInToken() {
        Sysuser user = new Sysuser();
        user.setId(42);
        user.setUuid("user-uuid");
        user.setMfaType("none");
        when(passkeyService.finishAuthentication(new JSONObject(), null)).thenReturn(user);
        when(sysuserService.rotateLoginSession(42)).thenReturn(1_800_000_000_123L);

        String token = service.loginWithPasskeyDirect(null, new JSONObject()).getToken();

        assertEquals(1_800_000_000_123L, JWTUtil.getTokenData(token, SECRET));
        verify(passkeyService).finishAuthentication(org.mockito.ArgumentMatchers.any(), isNull());
    }

    @Test
    void rotatedSessionMarkerRejectsOldTokenEvenWithinFormerToleranceWindow() {
        Long oldSession = 1_800_000_000_123L;
        Long newSession = oldSession + 1;
        String oldToken = JWTUtil.sign("user-uuid", SECRET, oldSession);

        assertTrue(JWTUtil.isCurrentSession(JWTUtil.getTokenData(oldToken, SECRET), oldSession));
        assertTrue(!JWTUtil.isCurrentSession(JWTUtil.getTokenData(oldToken, SECRET), newSession));
        assertTrue(!JWTUtil.isCurrentSession(0L, 0L));
        assertNull(JWTUtil.getTokenData("invalid", SECRET));
    }
}
