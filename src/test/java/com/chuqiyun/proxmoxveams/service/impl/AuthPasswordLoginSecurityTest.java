package com.chuqiyun.proxmoxveams.service.impl;

import com.chuqiyun.proxmoxveams.common.AuthException;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.entity.Sysuser;
import com.chuqiyun.proxmoxveams.security.LoginAttemptGuard;
import com.chuqiyun.proxmoxveams.security.MfaTicketService;
import com.chuqiyun.proxmoxveams.service.PasskeyService;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.utils.EncryptUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthPasswordLoginSecurityTest {
    private AuthServiceImpl service;
    private SysuserService users;
    private LoginAttemptGuard guard;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl();
        users = mock(SysuserService.class);
        guard = mock(LoginAttemptGuard.class);
        ReflectionTestUtils.setField(service, "sysuserService", users);
        ReflectionTestUtils.setField(service, "loginAttemptGuard", guard);
        ReflectionTestUtils.setField(service, "passkeyService", mock(PasskeyService.class));
        ReflectionTestUtils.setField(service, "mfaTicketService", mock(MfaTicketService.class));
        when(guard.isLocked(anyString(), anyString(), eq(false))).thenReturn(false);
        when(guard.isLocked(anyString(), anyString(), eq(true))).thenReturn(false);
    }

    @Test
    void unknownAndKnownPasswordFailuresUseTheSameBusinessCode() {
        Sysuser user = new Sysuser();
        user.setUsername("admin");
        user.setPassword(EncryptUtil.md5("CorrectPassword1"));
        when(users.getSysuserByUsername("admin")).thenReturn(user);

        AuthException unknown = assertThrows(AuthException.class,
                () -> service.loginWithPassword("missing", "wrong", "192.0.2.10"));
        AuthException wrongPassword = assertThrows(AuthException.class,
                () -> service.loginWithPassword("admin", "wrong", "192.0.2.10"));

        assertEquals(ResponseResult.RespCode.LOGIN_FAIL, unknown.getRespCode());
        assertEquals(ResponseResult.RespCode.LOGIN_FAIL, wrongPassword.getRespCode());
        verify(guard).recordFailure("missing", "192.0.2.10", false);
        verify(guard).recordFailure("admin", "192.0.2.10", true);
    }
}
