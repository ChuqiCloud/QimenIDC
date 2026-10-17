package com.chuqiyun.proxmoxveams.controller.admin;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.entity.SysPasskey;
import com.chuqiyun.proxmoxveams.entity.Sysuser;
import com.chuqiyun.proxmoxveams.service.PasskeyService;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.utils.JWTUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: 星禾
 * @Description: MFA policy changes require explicit intent and fresh password verification
 * @DateTime: 2026/10/17 16:01
 */
class SysProfileControllerSecurityTest {
    private SysProfileController controller;
    private SysuserService users;
    private PasskeyService passkeys;
    private Sysuser user;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        users = mock(SysuserService.class);
        passkeys = mock(PasskeyService.class);
        controller = new SysProfileController();
        ReflectionTestUtils.setField(controller, "secret", "test-secret");
        ReflectionTestUtils.setField(controller, "sysuserService", users);
        ReflectionTestUtils.setField(controller, "passkeyService", passkeys);
        user = new Sysuser();
        user.setId(1);
        user.setUuid("user-uuid");
        user.setMfaType("totp");
        user.setTotpSecret("encrypted-totp-secret");
        user.setTotpConfirmedTime(123L);
        when(users.getSysuserByUuid("user-uuid")).thenReturn(user);
        request = new MockHttpServletRequest();
        request.addHeader("Authorization", JWTUtil.sign("user-uuid", "test-secret"));
    }

    @Test
    void missingPolicyDoesNotImplicitlyDisableMfa() throws Exception {
        assertEquals(ResponseResult.RespCode.DATA_ERROR.getCode(), controller.updateMfaType(new JSONObject(), request).getCode());
        verify(users, never()).updateMfaSettings(any());
    }

    @Test
    void disablingOrSwitchingEnabledPolicyRequiresCurrentPassword() throws Exception {
        for (String policy : List.of("none", "passkey")) {
            JSONObject body = new JSONObject();
            body.put("mfaType", policy);

            assertEquals(ResponseResult.RespCode.OAUTH_PASSWORD_FAIL.getCode(), controller.updateMfaType(body, request).getCode());
        }
        verify(users, never()).updateMfaSettings(any());
    }

    @Test
    void passwordVerifiedPolicyChangeRemainsAvailable() throws Exception {
        when(users.verifyPassword(1, "current-password")).thenReturn(true);
        when(users.updateMfaSettings(any())).thenReturn(true);
        JSONObject body = new JSONObject();
        body.put("mfaType", "none");
        body.put("password", "current-password");

        assertEquals(ResponseResult.RespCode.OK.getCode(), controller.updateMfaType(body, request).getCode());
        assertEquals("none", user.getMfaType());
    }

    @Test
    void startingNewBindingCannotOverwriteConfirmedTotpKey() throws Exception {
        assertEquals(ResponseResult.RespCode.MFA_FAIL.getCode(), controller.bindTotp(request).getCode());
        verify(users, never()).startTotpBinding(anyInt(), anyString());
        assertEquals("encrypted-totp-secret", user.getTotpSecret());
    }

    @Test
    void existenceOfAnotherFactorDoesNotBypassPasswordOnUnbind() throws Exception {
        SysPasskey passkey = new SysPasskey();
        passkey.setId(3L);
        when(passkeys.listPasskeys(1)).thenReturn(List.of(passkey));

        assertEquals(ResponseResult.RespCode.OAUTH_PASSWORD_FAIL.getCode(), controller.unbindTotp(new JSONObject(), request).getCode());
        JSONObject body = new JSONObject();
        body.put("id", 3L);
        assertEquals(ResponseResult.RespCode.OAUTH_PASSWORD_FAIL.getCode(), controller.deletePasskey(body, request).getCode());

        verify(users, never()).clearTotpBinding(anyInt());
        verify(passkeys, never()).removePasskey(any(), anyInt());
    }
}
