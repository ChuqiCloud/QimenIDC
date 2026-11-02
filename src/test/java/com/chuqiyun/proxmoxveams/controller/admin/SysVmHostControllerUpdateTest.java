package com.chuqiyun.proxmoxveams.controller.admin;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.dto.VmParams;
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

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @Author: 星禾
 * @Description: 管理员改配接口保留具体失败原因，避免将关机失败和并发请求变成笼统错误。
 * @DateTime: 2026/11/2 10:30
 */
class SysVmHostControllerUpdateTest {
    private VmhostService service;
    private MockMvc mvc;
    private String token;

    @BeforeEach
    void setUp() {
        SysVmHostController controller = new SysVmHostController();
        service = mock(VmhostService.class);
        ReflectionTestUtils.setField(controller, "vmhostService", service);
        String secret = "admin-update-vm-test-secret";
        long session = System.currentTimeMillis();
        Sysuser admin = new Sysuser();
        admin.setUuid("update-vm-test-admin");
        admin.setLogindate(session);
        SysuserService users = mock(SysuserService.class);
        when(users.getSysuserByUuid(admin.getUuid())).thenReturn(admin);
        LicenseCheckInterceptor interceptor = new LicenseCheckInterceptor();
        interceptor.setSecret(secret);
        interceptor.setAdminPath("admin");
        ReflectionTestUtils.setField(interceptor, "sysuserService", users);
        token = JWTUtil.sign(admin.getUuid(), secret, session);
        mvc = MockMvcBuilders.standaloneSetup(controller).addInterceptors(interceptor).build();
    }

    @Test
    void postAndPutExposeSpecificShutdownFailureInsteadOfEnumMessage() throws Exception {
        when(service.updateVm(any(VmParams.class))).thenReturn(new UnifiedResultDto<>(
                UnifiedResultCode.ERROR_UNKNOWN, null, "创建虚拟机关机任务失败"));

        for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PUT)) {
            JSONObject response = submit(method, token);
            assertEquals(UnifiedResultCode.ERROR_UNKNOWN.getCode(), response.getInteger("code"));
            assertEquals("创建虚拟机关机任务失败", response.getString("message"));
        }
    }

    @Test
    void concurrentUpdateRejectionReachesClientWithSpecificReason() throws Exception {
        when(service.updateVm(any(VmParams.class))).thenReturn(new UnifiedResultDto<>(
                UnifiedResultCode.ERROR_INVALID_PARAM, null, "虚拟机配置修改中，请勿重复提交"));

        JSONObject response = submit(HttpMethod.PUT, token);

        assertEquals(UnifiedResultCode.ERROR_INVALID_PARAM.getCode(), response.getInteger("code"));
        assertEquals("虚拟机配置修改中，请勿重复提交", response.getString("message"));
    }

    @Test
    void unauthenticatedRequestCannotChangeConfiguration() throws Exception {
        JSONObject response = submit(HttpMethod.PUT, null);
        assertEquals(501002, response.getInteger("code"));
        verifyNoInteractions(service);
    }

    private JSONObject submit(HttpMethod method, String authorization) throws Exception {
        var request = request(method, "/admin/updateVm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostid\":17,\"bandwidth\":100}");
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        String body = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return JSONObject.parseObject(body);
    }
}
