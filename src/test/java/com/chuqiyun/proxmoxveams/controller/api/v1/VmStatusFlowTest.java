package com.chuqiyun.proxmoxveams.controller.api.v1;

import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.dto.VmParams;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class VmStatusFlowTest {
    private VmStatus controller;
    private VmhostService service;

    @BeforeEach
    void setUp() {
        controller = new VmStatus();
        service = mock(VmhostService.class);
        ReflectionTestUtils.setField(controller, "vmhostService", service);
    }

    @Test
    void failedQuotaPersistenceReturnsSpecificApiError() {
        VmParams params = new VmParams();
        params.setHostid(17);
        params.setExtraFlowLimit(20L);
        when(service.updateVm(params)).thenReturn(new UnifiedResultDto<>(
                UnifiedResultCode.ERROR_UNKNOWN, null, "保存虚拟机配置失败"));

        ResponseResult<?> result = (ResponseResult<?>) controller.addVmHostFlow(params);

        assertEquals(UnifiedResultCode.ERROR_UNKNOWN.getCode(), result.getCode());
        assertEquals("保存虚拟机配置失败", result.getMessage());
    }

    @Test
    void additiveAndResetFalseResultsAreNotReportedAsSuccess() {
        when(service.getById(17L)).thenReturn(new Vmhost());
        when(service.addVmHostFlow(17, 20L)).thenReturn(false);
        when(service.resetVmHostFlow(17)).thenReturn(false);

        ResponseResult<?> addition = (ResponseResult<?>) controller.addVmHostFlow(17L, 20L);
        ResponseResult<?> reset = (ResponseResult<?>) controller.resetVmHostFlow(17L);

        assertEquals(ResponseResult.RespCode.ERROR.getCode(), addition.getCode());
        assertEquals(ResponseResult.RespCode.ERROR.getCode(), reset.getCode());
    }
}
