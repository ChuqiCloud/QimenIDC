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
import org.springframework.web.bind.annotation.RequestMapping;
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
@RequestMapping("/api/v1")
public class GetVmInfo {
    @Resource
    private VmInfoService vmInfoService;

    /**
     * 获取虚拟机信息
     * 此方法通过hostId参数获取虚拟机的详细信息
     * 主要用于处理获取虚拟机信息的请求
     *
     * @param hostId 虚拟机主机ID，用于标识特定的虚拟机
     * @return 返回包含虚拟机信息的ResponseResult对象
     *         如果hostId为0，则返回失败的ResponseResult
     * @throws UnauthorizedException 如果用户未授权，抛出此异常
     */
    @PublicSysApiCheck
    @GetMapping("/pve/getVmInfo")
    public ResponseResult<Object> getNode(@RequestParam(name = "hostId") Integer hostId) throws UnauthorizedException {
        // 检查hostId是否为0，如果为0则返回错误信息
        if (hostId == 0) {
            return ResponseResult.fail("参数不能为空");
        }
        // 调用服务层方法获取虚拟机信息，并返回成功信息
        return ResponseResult.ok(vmInfoService.getVmHostById(hostId));
    }

    /**
     * 获取虚拟机列表
     * 此方法通过分页参数获取虚拟机的列表
     * 主要用于处理获取虚拟机列表的请求
     *
     * @param page 分页参数，用于标识当前页码
     * @param size 分页参数，用于标识每页显示的记录数
     * @return 返回包含虚拟机列表的ResponseResult对象
     *         如果分页参数为空，则返回失败的ResponseResult
     * @throws UnauthorizedException 如果用户未授权，抛出此异常
     */
    @PublicSysApiCheck
    @GetMapping(value = "/pve/getVmByPage")
    public ResponseResult<Object> getVmByPage(@RequestParam(name = "page",defaultValue = "1") Integer page,
                                              @RequestParam(name = "size",defaultValue = "20") Integer size)
            throws UnauthorizedException{
        HashMap<String, Object> vmByPage = vmInfoService.getVmByPage(page, size);
        return ResponseResult.ok(vmByPage);
    }

}
