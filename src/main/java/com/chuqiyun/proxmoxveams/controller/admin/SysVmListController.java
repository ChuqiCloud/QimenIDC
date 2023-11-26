package com.chuqiyun.proxmoxveams.controller.admin;

import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.service.VmInfoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;

/**
 * @author mryunqi
 * @date 2023/8/23
 */
@RestController
@RequestMapping("/{adminPath}")
public class SysVmListController {
    @Resource
    private VmInfoService vmInfoService;

    /**
    * @Author: mryunqi
    * @Description: 分页查询虚拟机列表
    * @DateTime: 2023/8/23 20:01
    */
    @AdminApiCheck
    @GetMapping(value = "/getVmByPage")
    public ResponseResult<Object> getVmByPage(@RequestParam(name = "page",defaultValue = "1") Integer page,
                                              @RequestParam(name = "size",defaultValue = "20") Integer size)
            throws UnauthorizedException{
        HashMap<String, Object> vmByPage = vmInfoService.getVmByPage(page, size);
        return ResponseResult.ok(vmByPage);
    }

    /**
    * @Author: mryunqi
    * @Description: 指定参数查找虚拟机
    * @DateTime: 2023/8/24 15:53
    */
    @AdminApiCheck
    @GetMapping(value = "/getVmByParam")
    public ResponseResult<Object> getVmByParam(@RequestParam(name = "page",defaultValue = "1") Integer page,
                                               @RequestParam(name = "size",defaultValue = "20") Integer size,
                                               @RequestParam(name = "param") String param,
                                               @RequestParam(name = "value") String value)
            throws UnauthorizedException{
        Object vmByParam = vmInfoService.getVmHostPageByParam(page, size, param, value);
        return ResponseResult.ok(vmByParam);
    }

    /**
    * @Author: mryunqi
    * @Description: 获取虚拟机分页列表,根据创建时间降序排列
    * @DateTime: 2023/11/26 20:15
    */
    @AdminApiCheck
    @GetMapping(value = "/getVmByPageOrderByCreateTime")
    public ResponseResult<Object> getVmByPageOrderByCreateTime(@RequestParam(name = "page",defaultValue = "1") Integer page,
                                                               @RequestParam(name = "size",defaultValue = "20") Integer size)
            throws UnauthorizedException{
        HashMap<String, Object> vmByPage = vmInfoService.getVmByPageOrderByCreateTime(page, size);
        return ResponseResult.ok(vmByPage);
    }

}
