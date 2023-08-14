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
public class SysConfigController {
    @Value("${config.admin_path}")
    private String ADMIN_PATH;
    @Resource
    private ConfigService configService;
    
    /**
    * @Author: mryunqi
    * @Description: 获取被控通讯密钥
    * @DateTime: 2023/8/12 14:33
    */
    @AdminApiCheck
    @GetMapping("/{adminPath}/getControlledSecretKey")
    public ResponseResult<Object> getControlledSecretKey(@PathVariable("adminPath") String adminPath)
            throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        return ResponseResult.ok(configService.getToken());
    }

    /**
    * @Author: mryunqi
    * @Description: 获取全局虚拟机默认系统盘大小
    * @DateTime: 2023/8/12 14:41
    */
    @AdminApiCheck
    @GetMapping("/{adminPath}/getVmDefaultDiskSize")
    public ResponseResult<Object> getVmDefaultDiskSize(@PathVariable("adminPath") String adminPath)
            throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
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
    @RequestMapping(value = "/{adminPath}/updateVmDefaultDiskSize",method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseResult<Object> updateVmDefaultDiskSize(@PathVariable("adminPath") String adminPath,
                                                          @RequestBody Map<String,Integer> diskSize)
            throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        Config config = configService.getById(1);
        config.setLinuxSystemDiskSize(diskSize.get("Linux"));
        config.setWinSystemDiskSize(diskSize.get("Windows"));
        if (configService.updateById(config)){
            return ResponseResult.ok();
        }
        return ResponseResult.fail("修改失败");
    }
}
