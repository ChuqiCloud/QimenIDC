package com.chuqiyun.proxmoxveams.controller.api.v1;

import com.chuqiyun.proxmoxveams.annotation.PublicSysApiCheck;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.dto.VmParams;
import com.chuqiyun.proxmoxveams.entity.*;
import com.chuqiyun.proxmoxveams.service.*;
import com.chuqiyun.proxmoxveams.utils.EntityHashMapConverterUtil;
import com.chuqiyun.proxmoxveams.utils.ModUtil;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;

import static com.chuqiyun.proxmoxveams.constant.TaskType.CREATE_VM;

/**
 * @author mryunqi
 * @date 2023/6/18
 */
@Slf4j
@RestController
public class CreateVm {
    @Resource
    private CreateVmService createVmService;
    @ApiOperation(value = "创建虚拟机", notes = "创建虚拟机")
    @PublicSysApiCheck
    @PostMapping("/api/v1/pve/cerateVM")
    public ResponseResult<Object> createVm(@RequestBody VmParams vmParams) throws UnauthorizedException {
        UnifiedResultDto<Object> resultDto = createVmService.createPveVmToParams(vmParams);
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
        }
        return ResponseResult.ok(resultDto.getResultCode().getMessage());
    }


}
