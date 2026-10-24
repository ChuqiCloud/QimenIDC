package com.chuqiyun.proxmoxveams.controller.admin;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.entity.Sysuser;
import com.chuqiyun.proxmoxveams.interceptor.LicenseCheckInterceptor;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.utils.JWTUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @Author: 星禾
 * @Description: 验证管理员密码重置的鉴权、参数校验和敏感响应隔离
 * @DateTime: 2026/10/24 16:37
 */
class SysVmHostControllerPasswordTest {
    private static final String PASSWORD = " new-vm-password ";
    private SysVmHostController controller;
    private VmhostService service;
    private MockMvc mvc;
    private String token;

    @BeforeEach
    void setUp() {
        controller = new SysVmHostController();
        service = mock(VmhostService.class);
        ReflectionTestUtils.setField(controller, "vmhostService", service);
        String secret = "admin-vm-password-test-secret";
        long session = System.currentTimeMillis();
        Sysuser user = new Sysuser();
        user.setUuid("password-test-admin");
        user.setLogindate(session);
        SysuserService users = mock(SysuserService.class);
        when(users.getSysuserByUuid(user.getUuid())).thenReturn(user);
        LicenseCheckInterceptor interceptor = new LicenseCheckInterceptor();
        interceptor.setSecret(secret);
        interceptor.setAdminPath("admin");
        ReflectionTestUtils.setField(interceptor, "sysuserService", users);
        token = JWTUtil.sign(user.getUuid(), secret, session);
        mvc = MockMvcBuilders.standaloneSetup(controller).addInterceptors(interceptor).build();
    }

    @Test
    void postAndPutSubmitExistingTaskFlowWithoutReturningSensitiveServiceData() throws Exception {
        when(service.resetVmPassword(834L, PASSWORD)).thenReturn(new UnifiedResultDto<>(
                UnifiedResultCode.SUCCESS, Map.of("newPassword", PASSWORD)));
        JSONObject body = body(834, PASSWORD);

        for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PUT)) {
            String content = mvc.perform(request(method, "/admin/resetVmPassword")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(body.toJSONString()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            JSONObject result = JSONObject.parseObject(content);
            assertEquals(ResponseResult.RespCode.OK.getCode(), result.getInteger("code"));
            assertNull(result.get("data"));
            assertFalse(content.contains(PASSWORD));
        }
        verify(service, times(2)).resetVmPassword(834L, PASSWORD);
    }

    @Test
    void missingAdminSessionCannotSubmitPasswordReset() throws Exception {
        for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PUT)) {
            String content = mvc.perform(request(method, "/admin/resetVmPassword")
                            .contentType(MediaType.APPLICATION_JSON).content(body(834, PASSWORD).toJSONString()))
                    .andReturn().getResponse().getContentAsString();
            assertEquals(501002, JSONObject.parseObject(content).getInteger("code"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void invalidHostIdsNeverReachServiceOrTruncateToAnotherVirtualMachine() throws Exception {
        assertInvalidHostId(null);
        assertInvalidHostId(new JSONObject());
        for (Object id : List.of(0, -1, "not-a-number", "", new BigDecimal("834.5"),
                new BigDecimal("834.0"), new BigInteger("9223372036854775808"), true,
                new JSONObject(), List.of(834))) {
            assertInvalidHostId(body(id, PASSWORD));
        }
        verifyNoInteractions(service);
    }

    @Test
    void emptyOrNonStringPasswordsCannotSubmitTask() throws Exception {
        JSONObject missingPassword = new JSONObject();
        missingPassword.put("hostId", 834);
        ResponseResult<?> missing = (ResponseResult<?>) controller.resetVmPassword(missingPassword);
        assertEquals(UnifiedResultCode.ERROR_NEW_PASSWORD_NOT_NULL.getCode(), missing.getCode());
        for (Object password : List.of("", " \t\r\n ", 123456, new JSONObject())) {
            ResponseResult<?> result = (ResponseResult<?>) controller.resetVmPassword(body(834, password));
            assertEquals(UnifiedResultCode.ERROR_NEW_PASSWORD_NOT_NULL.getCode(), result.getCode());
        }
        verifyNoInteractions(service);
    }

    @Test
    void serviceFailurePreservesItsCodeAndMessage() throws Exception {
        when(service.resetVmPassword(834L, PASSWORD)).thenReturn(new UnifiedResultDto<>(
                UnifiedResultCode.ERROR_RESET_PASSWORD_FAILED, null, "创建密码重置任务失败"));

        ResponseResult<?> result = (ResponseResult<?>) controller.resetVmPassword(body(834, PASSWORD));

        assertEquals(UnifiedResultCode.ERROR_RESET_PASSWORD_FAILED.getCode(), result.getCode());
        assertEquals("创建密码重置任务失败", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void integerStringHostIdAndPasswordWhitespaceArePreserved() throws Exception {
        when(service.resetVmPassword(834L, PASSWORD)).thenReturn(new UnifiedResultDto<>(
                UnifiedResultCode.SUCCESS, null));

        ResponseResult<?> result = (ResponseResult<?>) controller.resetVmPassword(body("834", PASSWORD));

        assertEquals(ResponseResult.RespCode.OK.getCode(), result.getCode());
        verify(service).resetVmPassword(834L, PASSWORD);
    }

    private void assertInvalidHostId(JSONObject body) throws Exception {
        ResponseResult<?> result = (ResponseResult<?>) controller.resetVmPassword(body);
        assertEquals(UnifiedResultCode.ERROR_INVALID_PARAM.getCode(), result.getCode());
        assertEquals("hostId必须是正整数", result.getMessage());
    }

    private JSONObject body(Object hostId, Object password) {
        JSONObject body = new JSONObject();
        body.put("hostId", hostId);
        body.put("newPassword", password);
        return body;
    }
}
