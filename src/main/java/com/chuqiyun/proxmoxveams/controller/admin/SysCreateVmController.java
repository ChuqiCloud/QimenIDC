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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author mryunqi
 * @date 2023/8/6
 */
@Slf4j
@RestController
public class SysCreateVmController {
    @Value("${config.admin_path}")
    private String ADMIN_PATH;
    @Resource
    private CreateVmService createVmService;

    /**
    * @Author: mryunqi
    * @Description: 创建虚拟机
    * @DateTime: 2023/8/6 16:13
    */
    @AdminApiCheck
    @PostMapping("/{adminPath}/createVm")
    public ResponseResult<Object> createVm(@PathVariable("adminPath") String adminPath,
                         @RequestBody VmParams vmParams) throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        UnifiedResultDto<Object> resultDto = createVmService.createPveVmToParams(vmParams,false);
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
        }
        return ResponseResult.ok(resultDto.getResultCode().getMessage());
    }
}
