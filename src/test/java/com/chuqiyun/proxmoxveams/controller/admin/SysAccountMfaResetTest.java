package com.chuqiyun.proxmoxveams.controller.admin;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.entity.Sysuser;
import com.chuqiyun.proxmoxveams.interceptor.LicenseCheckInterceptor;
import com.chuqiyun.proxmoxveams.security.MfaTicketService;
import com.chuqiyun.proxmoxveams.service.SysuserMfaService;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.utils.JWTUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * @Author: 星禾
 * @Description: 验证管理员重置安全因子的路由、权限、操作人密码和参数边界
 * @DateTime: 2026/11/19 18:26
 */
class SysAccountMfaResetTest {
    private static final String SECRET = "mfa-reset-test-secret";
    private static final String PASSWORD = "operator-password";
    private static final List<String> PATHS = List.of("/admin/resetSysuserTotp", "/admin/resetSysuserPasskey");
    private MockMvc mvc;
    private SysuserMfaService resets;
    private SysuserService users;
    private String token;

    @BeforeEach
    void setup() {
        users = mock(SysuserService.class);
        resets = mock(SysuserMfaService.class);
        SysAccountController controller = new SysAccountController();
        ReflectionTestUtils.setField(controller, "sysuserService", users);
        ReflectionTestUtils.setField(controller, "sysuserMfaService", resets);
        ReflectionTestUtils.setField(controller, "secret", SECRET);
        Sysuser operator = new Sysuser();
        operator.setId(7);
        operator.setUuid("reset-operator");
        operator.setLogindate(System.currentTimeMillis());
        when(users.getSysuserByUuid(operator.getUuid())).thenReturn(operator);
        when(users.verifyPassword(7, PASSWORD)).thenReturn(true);
        token = JWTUtil.sign(operator.getUuid(), SECRET, operator.getLogindate());
        LicenseCheckInterceptor interceptor = new LicenseCheckInterceptor();
        interceptor.setSecret(SECRET);
        interceptor.setAdminPath("admin");
        ReflectionTestUtils.setField(interceptor, "sysuserService", users);
        mvc = MockMvcBuilders.standaloneSetup(controller).addInterceptors(interceptor).build();
    }

    @Test
    void endpointsResetRequestedTargetAndReturnNoCredentials() throws Exception {
        when(resets.resetTotp(123)).thenReturn(true);
        when(resets.resetPasskey(123)).thenReturn(true);
        for (String path : PATHS) {
            JSONObject result = call(path, token, body(123, PASSWORD));
            assertEquals(ResponseResult.RespCode.OK.getCode(), result.getInteger("code"));
            assertFalse(result.toJSONString().contains(PASSWORD));
            assertFalse(result.containsKey("credential"));
            assertFalse(result.containsKey("secret"));
        }
        verify(resets).resetTotp(123);
        verify(resets).resetPasskey(123);
    }

    @Test
    void missingTokenMfaTicketAndOldSessionCannotResetAnyone() throws Exception {
        for (String path : PATHS) {
            assertEquals(501002, call(path, null, body(123, PASSWORD)).getInteger("code"));
            String ticket = new MfaTicketService(SECRET).issue("reset-operator", "totp");
            assertEquals(501002, call(path, ticket, body(123, PASSWORD)).getInteger("code"));
            String oldToken = JWTUtil.sign("reset-operator", SECRET, 1L);
            assertEquals(501002, call(path, oldToken, body(123, PASSWORD)).getInteger("code"));
        }
        verifyNoInteractions(resets);
    }

    @Test
    void missingOrIncorrectOperatorPasswordCannotResetEvenSelf() throws Exception {
        for (String path : PATHS) {
            for (String password : List.of("", "wrong-password")) {
                assertEquals(ResponseResult.RespCode.OAUTH_PASSWORD_FAIL.getCode(),
                        call(path, token, body(7, password)).getInteger("code"));
            }
            JSONObject missing = body(123, PASSWORD);
            missing.remove("password");
            assertEquals(ResponseResult.RespCode.OAUTH_PASSWORD_FAIL.getCode(),
                    call(path, token, missing).getInteger("code"));
        }
        verifyNoInteractions(resets);
    }

    @Test
    void invalidIdsNeverReachResetServiceOrTruncateToAnotherAccount() throws Exception {
        for (String path : PATHS) {
            assertEquals(ResponseResult.RespCode.DATA_ERROR.getCode(),
                    call(path, token, new JSONObject()).getInteger("code"));
            for (Object id : List.of(0, -1, "", "invalid", new BigDecimal("123.5"),
                    new BigInteger("2147483648"), true, new JSONObject(), List.of(123))) {
                assertEquals(ResponseResult.RespCode.DATA_ERROR.getCode(),
                        call(path, token, body(id, PASSWORD)).getInteger("code"));
            }
        }
        verifyNoInteractions(resets);
    }

    @Test
    void nonexistentTargetReturnsClearBusinessError() throws Exception {
        for (String path : PATHS) {
            JSONObject result = call(path, token, body(123, PASSWORD));
            assertEquals(ResponseResult.RespCode.DATA_ERROR.getCode(), result.getInteger("code"));
            assertEquals("用户不存在", result.getString("message"));
        }
    }

    private JSONObject call(String path, String auth, JSONObject body) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(body.toJSONString());
        if (auth != null) {
            request.header("Authorization", auth);
        }
        return JSONObject.parseObject(mvc.perform(request).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private JSONObject body(Object id, String password) {
        JSONObject body = new JSONObject();
        body.put("id", id);
        body.put("password", password);
        return body;
    }
}
