package com.chuqiyun.proxmoxveams.controller.admin;

import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.entity.Config;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * @author mryunqi
 * @date 2023/8/12
 */
@RestController
@RequestMapping("/{adminPath}")
public class SysConfigController {
    @Resource
    private ConfigService configService;
    
    /**
    * @Author: mryunqi
    * @Description: 获取被控通讯密钥
    * @DateTime: 2023/8/12 14:33
    */
    @AdminApiCheck
    @GetMapping("/getControlledSecretKey")
    public ResponseResult<Object> getControlledSecretKey()
            throws UnauthorizedException {
        return ResponseResult.ok(configService.getToken());
    }

    /**
    * @Author: mryunqi
    * @Description: 获取全局虚拟机默认系统盘大小
    * @DateTime: 2023/8/12 14:41
    */
    @AdminApiCheck
    @GetMapping("/getVmDefaultDiskSize")
    public ResponseResult<Object> getVmDefaultDiskSize()
            throws UnauthorizedException {
        Map<String,Integer> result = new HashMap<>();
        result.put("Linux",configService.getLinuxSystemDiskSize());
        result.put("Windows",configService.getWinSystemDiskSize());
        return ResponseResult.ok(result);
    }

    /**
    * @Author: mryunqi
    * @Description: 修改全局虚拟机默认系统盘大小
    * @DateTime: 2023/8/12 14:45
    */
    @AdminApiCheck
    @RequestMapping(value = "/updateVmDefaultDiskSize",method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseResult<Object> updateVmDefaultDiskSize(@RequestBody Map<String,Integer> diskSize)
            throws UnauthorizedException {
        Config config = configService.getById(1);
        config.setLinuxSystemDiskSize(diskSize.get("Linux"));
        config.setWinSystemDiskSize(diskSize.get("Windows"));
        if (configService.updateById(config)){
            return ResponseResult.ok();
        }
        return ResponseResult.fail("修改失败");
    }

    /**
    * @Author: mryunqi
    * @Description: 获取所有系统配置
    * @DateTime: 2024/2/16 9:34
    */
    @AdminApiCheck
    @GetMapping("/getAllConfig")
    public ResponseResult<Object> getAllConfig()
            throws UnauthorizedException {
        return ResponseResult.ok(configService.getConfig());
    }

    /**
    * @Author: mryunqi
    * @Description: 修改系统配置
    * @DateTime: 2024/2/16 9:35
    */
    @AdminApiCheck
    @RequestMapping(value = "/updateConfig",method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseResult<Object> updateConfig(@RequestBody Config config)
            throws UnauthorizedException {
        if (configService.updateConfig(config)){
            return ResponseResult.ok();
        }
        return ResponseResult.fail("修改失败");
    }
}
