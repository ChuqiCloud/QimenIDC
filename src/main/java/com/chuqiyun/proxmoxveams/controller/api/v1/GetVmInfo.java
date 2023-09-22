package com.chuqiyun.proxmoxveams.controller.api.v1;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.annotation.PublicSysApiCheck;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.VmInfoService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;

/**
 * @author mryunqi
 * @date 2023/7/18
 */
@Slf4j
@RestController
public class GetVmInfo {
    @Resource
    private VmInfoService vmInfoService;

    @PublicSysApiCheck
    @GetMapping("/api/v1/pve/getVmInfo")
    public ResponseResult<Object> getNode(@RequestParam(name = "hostId") Integer hostId) throws UnauthorizedException {
        if (hostId == 0) {
            return ResponseResult.fail("参数不能为空");
        }
        return ResponseResult.ok(vmInfoService.getVmHostByVmId(hostId));
    }
}
