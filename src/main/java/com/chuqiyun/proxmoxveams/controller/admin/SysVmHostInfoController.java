package com.chuqiyun.proxmoxveams.controller.admin;

import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.service.VmInfoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * @author mryunqi
 * @date 2023/8/28
 */
@RestController
@RequestMapping("/{adminPath}")
public class SysVmHostInfoController {
    @Resource
    private VmInfoService vmInfoService;

    /**
    * @Author: mryunqi
    * @Description: 获取虚拟机主机信息
    * @DateTime: 2023/8/28 17:17
    */
    @AdminApiCheck
    @GetMapping(value = "/getVmHostInfo")
    public Object getVmHostInfo(@RequestParam(name = "vmId",defaultValue="0") Integer vmId) throws UnauthorizedException {
        // 判断参数是否为0
        if (vmId == 0) {
            return ResponseResult.fail("参数不能为空");
        }
        return ResponseResult.ok(vmInfoService.getVmHostByVmId(vmId));
    }

    /**
    * @Author: mryunqi
    * @Description: 获取虚拟机历史负载
    * @DateTime: 2023/8/28 20:08
    */
    @AdminApiCheck
    @GetMapping(value = "/getVmHostRrdData")
    public Object getVmHostRrdData(@RequestParam(name = "vmId") Integer vmId,
                                   @RequestParam(name = "timeframe",defaultValue = "hour") String timeframe,
                                   @RequestParam(name = "cf",defaultValue = "AVERAGE") String cf) throws UnauthorizedException {
        return ResponseResult.ok(vmInfoService.getVmInfoRrdData(vmId,timeframe, cf).getJSONArray("data"));
    }
}
