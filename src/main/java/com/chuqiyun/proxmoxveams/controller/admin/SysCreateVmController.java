package com.chuqiyun.proxmoxveams.controller.admin;

import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.dto.VmParams;
import com.chuqiyun.proxmoxveams.service.*;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * @author mryunqi
 * @date 2023/8/6
 */
@Slf4j
@RestController
@RequestMapping("/{adminPath}")
public class SysCreateVmController {
    @Resource
    private CreateVmService createVmService;

    /**
    * @Author: mryunqi
    * @Description: 创建虚拟机
    * @DateTime: 2023/8/6 16:13
    */
    @AdminApiCheck
    @PostMapping("/createVm")
    public ResponseResult<Object> createVm(@RequestBody VmParams vmParams) throws UnauthorizedException {
        UnifiedResultDto<Object> resultDto = createVmService.createPveVmToParams(vmParams,false);
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
        }
        return ResponseResult.ok(resultDto.getResultCode().getMessage());
    }
}
