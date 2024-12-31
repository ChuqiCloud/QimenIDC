package com.chuqiyun.proxmoxveams.controller.admin;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.VmInfoService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;

/**
 * @author mryunqi
 * @date 2023/8/28
 */
@RestController
@RequestMapping("/{adminPath}")
public class SysVmHostInfoController {
    @Resource
    private VmInfoService vmInfoService;
    @Resource
    private VmhostService vmhostService;

    /**
    * @Author: mryunqi
    * @Description: 获取虚拟机主机信息
    * @DateTime: 2023/8/28 17:17
    */
    @AdminApiCheck
    @GetMapping(value = "/getVmHostInfo")
    public Object getVmHostInfo(@RequestParam(name = "hostId",defaultValue="0") Integer hostId) throws UnauthorizedException {
        // 判断参数是否为0
        if (hostId == 0) {
            return ResponseResult.fail("参数不能为空");
        }
        return ResponseResult.ok(vmInfoService.getVmHostById(hostId));
    }

    /**
    * @Author: mryunqi
    * @Description: 获取虚拟机历史负载
    * @DateTime: 2023/8/28 20:08
    */
    @AdminApiCheck
    @GetMapping(value = "/getVmHostRrdData")
    public Object getVmHostRrdData(@RequestParam(name = "hostId") Integer hostId,
                                   @RequestParam(name = "timeframe",defaultValue = "hour") String timeframe,
                                   @RequestParam(name = "cf",defaultValue = "AVERAGE") String cf) throws UnauthorizedException {
        return ResponseResult.ok(vmInfoService.getVmInfoRrdData(hostId,timeframe, cf).getJSONArray("data"));
    }

    /**
    * @Author: mryunqi
    * @Description: 获取虚拟机总数
    * @DateTime: 2023/11/26 20:20
    */
    @AdminApiCheck
    @GetMapping(value = "/getVmCount")
    public Object getVmCount() throws UnauthorizedException {
        return ResponseResult.ok(vmInfoService.getVmCount());
    }

    /**
    * @Author: mryunqi
    * @Description: 分页获取指定状态的虚拟机列表
    * @DateTime: 2023/11/26 22:02
    */
    @AdminApiCheck
    @GetMapping(value = "/getVmByStatus")
    public Object getVmByStatus(@RequestParam(name = "status") Integer status,
                                @RequestParam(name = "page",defaultValue = "1") Long page,
                                @RequestParam(name = "size",defaultValue = "20") Long size) throws UnauthorizedException {
        return ResponseResult.ok(vmhostService.getVmhostByStatus(page,size,status));
    }

    /**
    * @Author: mryunqi
    * @Description: 获取指定状态的虚拟机总数
    * @DateTime: 2023/11/26 22:11
    */
    @AdminApiCheck
    @GetMapping(value = "/getVmCountByStatus")
    public Object getVmCountByStatus(@RequestParam(name = "status") Integer status) throws UnauthorizedException {
        return ResponseResult.ok(vmhostService.getVmhostCountByStatus(status));
    }
    /**
     * @Author: 星禾
     * @Description: 添加虚拟机规则
     * @DateTime: 2024/12/30 16:55
     */
    @AdminApiCheck
    @RequestMapping(value = "/nat/add",method = {RequestMethod.POST,RequestMethod.PUT})
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
     * @DateTime: 2024/12/30 17:00
     */
    @AdminApiCheck
    @RequestMapping(value = "/nat/del",method = {RequestMethod.POST,RequestMethod.PUT})
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
     * @DateTime: 2024/12/29 20:15
     */
    @AdminApiCheck
    @GetMapping(value = "/nat/getVm")
    public Object getVmNat(@RequestParam(name = "page",defaultValue = "1") Integer page,
                           @RequestParam(name = "size",defaultValue = "20") Integer size,
                           @RequestParam(name = "hostId") Integer hostId
    ) throws UnauthorizedException {
        return vmhostService.getVmhostNatByVmid(page,size,hostId);
    }
}
