package com.chuqiyun.proxmoxveams.controller.api.v1;
import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.annotation.PublicSysApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * @author 星禾
 * @date  2024/12/30
 */
@RestController
@RequestMapping("/api/v1")
public class Nat {
    @Resource
    private VmhostService vmhostService;
    /**
     * @Author: 星禾
     * @Description: 添加Nat规则
     * @DateTime: 2024/12/30 17:25
     */
    @PublicSysApiCheck
    @RequestMapping(value = "/pve/nat/add",method = {RequestMethod.POST,RequestMethod.PUT})
    public Object addVmNat(@RequestBody JSONObject params) throws UnauthorizedException {
        Boolean result = vmhostService.addVmhostNat(params.getInteger("source_port"), params.getString("destination_ip"), params.getInteger("destination_port"), params.getString("protocol") , params.getInteger("vm"));
        if( result ) {
            return ResponseResult.ok();
        } else {
            return ResponseResult.fail();
        }
    }
    /**
     * @Author: 星禾
     * @Description: 删除虚拟机规则
     * @DateTime: 2024/12/30 17:25
     */
    @PublicSysApiCheck
    @RequestMapping(value = "/pve/nat/del",method = {RequestMethod.POST,RequestMethod.PUT})
    public Object delVmNat(@RequestBody JSONObject params) throws UnauthorizedException {
        Boolean result = vmhostService.delVmhostNat(params.getInteger("source_port"), params.getString("destination_ip"), params.getInteger("destination_port"), params.getString("protocol") , params.getInteger("vm"));
        if( result ) {
            return ResponseResult.ok();
        } else {
            return ResponseResult.fail();
        }
    }
    /**
     * @Author: 星禾
     * @Description: 根据VM数据库ID获取虚拟机Nat规则
     * @DateTime: 2024/12/30 17:25
     */
    @PublicSysApiCheck
    @GetMapping(value = "/pve/nat/getVm")
    public Object getVmNat(@RequestParam(name = "page",defaultValue = "1") Integer page,
                           @RequestParam(name = "size",defaultValue = "20") Integer size,
                           @RequestParam(name = "hostId") Integer hostId
    ) throws UnauthorizedException {
        return vmhostService.getVmhostNatByVmid(page,size,hostId);
    }
}