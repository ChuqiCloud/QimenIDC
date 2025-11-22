package com.chuqiyun.proxmoxveams.controller.api.v1;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.annotation.PublicSysApiCheck;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;

/**
 * @author mryunqi
 * @date 2023/7/18
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
public class VmStatus {
    @Resource
    private VmhostService vmhostService;

    /**
    * @Author: mryunqi
    * @Description: 虚拟机操作接口
    * @DateTime: 2023/9/22 23:43
    */
    @PublicSysApiCheck
    @RequestMapping(value = "/pve/power/{hostId}/{action}",method = {RequestMethod.POST,RequestMethod.PUT})
    public ResponseResult<String> putVmStatus(@PathVariable(name = "hostId") Integer hostId,
                                              @PathVariable(name = "action") String action) throws UnauthorizedException {
        // 判断虚拟机是否存在
        if (vmhostService.getById(hostId) == null) {
            return ResponseResult.fail("虚拟机不存在");
        }
        // 判断action是否合法
        if (!"start".equals(action) && !"stop".equals(action) && !"shutdown".equals(action) && !"reboot".equals(action)
                && !"pause".equals(action) && !"unpause".equals(action) && !"suspend".equals(action) && !"resume".equals(action)) {
            return ResponseResult.fail("action不合法");
        }
        HashMap<String, Object> result = vmhostService.power(hostId, action, null);
        if (result == null) {
            return ResponseResult.fail("操作失败");
        }
        Boolean status = (Boolean) result.get("status");
        if (!status) {
            return ResponseResult.fail(result.get("msg").toString());
        }
        return ResponseResult.ok("操作成功");
    }

    /**
    * @Author: mryunqi
    * @Description: 重装系统接口
    * @DateTime: 2023/9/22 23:43
    */
    @PublicSysApiCheck
    @RequestMapping(value = "/pve/reinstall",method = {RequestMethod.POST,RequestMethod.PUT})
    public Object reinstall(@RequestBody JSONObject params) throws UnauthorizedException {
        UnifiedResultDto<Object> resultDto = vmhostService.resetVmOs(params.getLong("hostId"), params.getString("os"), params.getString("newPassword") , params.getBoolean("resetDataDisk"));
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
        }
        return ResponseResult.ok(resultDto.getResultCode().getMessage());
    }

    /**
    * @Author: mryunqi
    * @Description: 删除虚拟机接口
    * @DateTime: 2023/9/22 23:45
    */
    @PublicSysApiCheck
    @RequestMapping(value = "/pve/delete/{hostId}",method = {RequestMethod.POST,RequestMethod.PUT,RequestMethod.DELETE})
    public Object delete(@PathVariable("hostId") Long hostId) throws UnauthorizedException {
        UnifiedResultDto<Object> resultDto = vmhostService.deleteVm(hostId);
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
        }
        return ResponseResult.ok(resultDto.getResultCode().getMessage());
    }
    /**
     * @Author: 星禾
     * @Description: 重置虚拟机流量接口
     * @DateTime: 2025/11/21 23:07
     */
    @PublicSysApiCheck
    @RequestMapping(value = "/pve/resetVmHostFlow/{hostId}",method = {RequestMethod.POST,RequestMethod.PUT})
    public Object resetVmHostFlow(@PathVariable("hostId") Long hostId) throws UnauthorizedException {
        // 判断虚拟机是否存在
        if (vmhostService.getById(hostId) == null) {
            return ResponseResult.fail("虚拟机不存在");
        }
        Boolean result = vmhostService.resetVmHostFlow(Math.toIntExact(hostId));
        if (result == null) {
            return ResponseResult.fail("操作失败");
        }
        return ResponseResult.ok("操作成功");
    }
    /**
     * @Author: 星禾
     * @Description: 添加虚拟机流量包接口
     * @DateTime: 2025/11/21 23:07
     */
    @PublicSysApiCheck
    @RequestMapping(value = "/pve/addVmHostFlow/{hostId}",method = {RequestMethod.POST,RequestMethod.PUT})
    public Object addVmHostFlow(@PathVariable("hostId") Long hostId,@RequestParam("flow") Long flow) throws UnauthorizedException {
        // 判断虚拟机是否存在
        if (vmhostService.getById(hostId) == null) {
            return ResponseResult.fail("虚拟机不存在");
        }
        if (flow < 0 || flow > 1000000){
            return ResponseResult.fail("流量包不能过大（1000000G）或小于0");
        }
        Boolean result = vmhostService.resetVmHostFlow(Math.toIntExact(hostId));
        if (result == null) {
            return ResponseResult.fail("操作失败");
        }
        return ResponseResult.ok("操作成功");
    }
}
