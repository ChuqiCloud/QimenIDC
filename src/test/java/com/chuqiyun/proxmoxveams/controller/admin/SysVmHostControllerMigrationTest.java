package com.chuqiyun.proxmoxveams.controller.admin;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.dto.VmMigrationTaskView;
import com.chuqiyun.proxmoxveams.entity.Sysuser;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.interceptor.LicenseCheckInterceptor;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.service.VmMigrationService;
import com.chuqiyun.proxmoxveams.utils.JWTUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SysVmHostControllerMigrationTest {
    private VmMigrationService service;
    private MockMvc mvc;
    private String token;

    @BeforeEach
    void setUp() {
        SysVmHostController controller = new SysVmHostController();
        service = mock(VmMigrationService.class);
        ReflectionTestUtils.setField(controller, "vmMigrationService", service);
        String secret = "migration-termination-test-secret";
        long session = System.currentTimeMillis();
        Sysuser admin = new Sysuser();
        admin.setUuid("migration-test-admin");
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
    void authenticatedPostAndPutTerminateWithoutRequestBodyAndReturnTerminalView() throws Exception {
        Task task = new Task();
        task.setId(1158);
        VmMigrationTaskView view = new VmMigrationTaskView();
        view.setTaskId(1158);
        view.setStage("TERMINATED");
        view.setResumable(false);
        when(service.terminate(1158)).thenReturn(task);
        when(service.getTaskView(1158)).thenReturn(view);

        for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PUT)) {
            String content = mvc.perform(request(method, "/admin/migrateVm/1158/terminate")
                            .header("Authorization", token))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            JSONObject response = JSONObject.parseObject(content);
            assertEquals(ResponseResult.RespCode.OK.getCode(), response.getInteger("code"));
            assertEquals("TERMINATED", response.getJSONObject("data").getString("stage"));
            assertFalse(response.getJSONObject("data").getBooleanValue("resumable"));
        }
        verify(service, times(2)).terminate(1158);
    }

    @Test
    void unauthenticatedRequestsCannotTerminateOrQueryTask() throws Exception {
        for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PUT)) {
            String content = mvc.perform(request(method, "/admin/migrateVm/1158/terminate"))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertEquals(501002, JSONObject.parseObject(content).getInteger("code"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void guardRejectionReachesClientWithoutPretendingTerminationSucceeded() throws Exception {
        when(service.terminate(1158)).thenThrow(new IllegalStateException("目标VMID 126 仍存在"));

        String content = mvc.perform(request(HttpMethod.POST, "/admin/migrateVm/1158/terminate")
                        .header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JSONObject response = JSONObject.parseObject(content);
        assertEquals(ResponseResult.RespCode.ERROR.getCode(), response.getInteger("code"));
        assertEquals("目标VMID 126 仍存在", response.getString("message"));
    }
}
