package com.chuqiyun.proxmoxveams.controller.api.v1;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.annotation.PublicSysApiCheck;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
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
    private MasterService masterService;
    @Resource
    private VmhostService vmhostService;

    @PublicSysApiCheck
    @GetMapping("/api/v1/getVmInfo")
    public ResponseResult<HashMap<String, Object>> getNode(@RequestParam(name = "hostId") Integer hostId) throws UnauthorizedException {
        Vmhost vmhost = vmhostService.getById(hostId);
        if (vmhost == null) {
            return ResponseResult.fail("虚拟机不存在");
        }
        Master node = masterService.getById(vmhost.getNodeid());
        if (node == null) {
            return ResponseResult.fail("节点不存在");
        }
        // 从proxmox获取虚拟机信息
        HashMap<String, Object> result = new HashMap<>();
        result.put("info", vmhost);
        JSONObject vmJsonInfo = masterService.getVmStatusCurrent(node.getId(),vmhost.getVmid());
        result.put("status", vmJsonInfo);
        return ResponseResult.ok(result);
    }
}
