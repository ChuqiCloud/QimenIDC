package com.chuqiyun.proxmoxveams.controller.admin;

import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.service.VmInfoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author mryunqi
 * @date 2023/8/28
 */
@RestController
public class SysVmHostInfoController {
    @Value("${config.admin_path}")
    private String ADMIN_PATH;
    @Resource
    private VmInfoService vmInfoService;

    /**
    * @Author: mryunqi
    * @Description: 获取虚拟机主机信息
    * @DateTime: 2023/8/28 17:17
    */
    @AdminApiCheck
    @GetMapping(value = "/{adminPath}/getVmHostInfo")
    public Object getVmHostInfo(@PathVariable("adminPath") String adminPath,
                                @RequestParam(name = "vmId") Integer vmId) throws UnauthorizedException {
        if (!ADMIN_PATH.equals(adminPath)){
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        return ResponseResult.ok(vmInfoService.getVmHostByVmId(vmId));
    }

    /**
    * @Author: mryunqi
    * @Description: 获取虚拟机历史负载
    * @DateTime: 2023/8/28 20:08
    */
    @AdminApiCheck
    @GetMapping(value = "/{adminPath}/getVmHostRrdData")
    public Object getVmHostRrdData(@PathVariable("adminPath") String adminPath,
                                   @RequestParam(name = "vmId") Integer vmId,
                                   @RequestParam(name = "timeframe") String timeframe,
                                   @RequestParam(name = "cf") String cf) throws UnauthorizedException {
        if (!ADMIN_PATH.equals(adminPath)){
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        return ResponseResult.ok(vmInfoService.getVmInfoRrdData(vmId,timeframe, cf));
    }
}
