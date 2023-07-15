package com.chuqiyun.proxmoxveams.controller.admin;

import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.entity.Sysapi;
import com.chuqiyun.proxmoxveams.service.SysapiService;
import com.chuqiyun.proxmoxveams.utils.ResponseResult;
import com.chuqiyun.proxmoxveams.utils.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Objects;
import java.util.UUID;

/**
 * @author mryunqi
 * @date 2023/5/8
 */
@RestController
public class SysApiController {
    @Value("${config.admin_path}")
    private String ADMIN_PATH;

    @Value("${config.secret}")
    private String secret;

    @Resource
    private SysapiService sysapiService;

    /**
    * @Author: mryunqi
    * @Description: 添加API
    * @DateTime: 2023/5/8 17:19
    */
    @AdminApiCheck
    @PostMapping("/{adminPath}/insertApiKey")
    public ResponseResult insertApiKey(@PathVariable("adminPath") String adminPath)throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        String appId = String.valueOf(System.currentTimeMillis());
        String appKey = UUID.randomUUID().toString().replace("-", "") + Thread.currentThread().getId();
        Sysapi sysapi = new Sysapi();
        sysapi.setAppkey(appKey);
        sysapi.setAppid(appId);
        if (sysapiService.save(sysapi)){
            return ResponseResult.ok(sysapi);
        }
        return ResponseResult.fail("添加失败");
    }

}
