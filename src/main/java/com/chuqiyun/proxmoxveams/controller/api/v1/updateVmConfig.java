package com.chuqiyun.proxmoxveams.controller.api.v1;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.annotation.PublicSysApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.dto.RenewalParams;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.service.VmInfoService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author mryunqi
 * @date 2023/9/29
 */
@RestController
public class updateVmConfig {
    @Resource
    private VmhostService vmhostService;

    /**
    * @Author: mryunqi
    * @Description: 修改虚拟机密码
    * @DateTime: 2023/9/29 15:05
    */
    @PublicSysApiCheck
    @RequestMapping(value = "/api/v1/pve/updateVmConfig/restPassword",method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseResult<Object> restPassword(@RequestBody JSONObject params) throws UnauthorizedException {
        UnifiedResultDto<Object> resultDto = vmhostService.resetVmPassword(params.getLong("hostId"),params.getString("newPassword"));
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
        }
        return ResponseResult.ok(resultDto.getData());
    }

    /**
    * @Author: mryunqi
    * @Description: 续期
    * @DateTime: 2023/9/29 18:14
    */
    @PublicSysApiCheck
    @RequestMapping(value = "/api/v1/pve/updateVmConfig/renewal",method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseResult<Object> renewal(@RequestBody RenewalParams params) throws UnauthorizedException {
        UnifiedResultDto<Object> resultDto = vmhostService.updateVmhostExpireTime(params);
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
        }
        return ResponseResult.ok(resultDto.getData());
    }
}
