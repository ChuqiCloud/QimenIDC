package com.chuqiyun.proxmoxveams.controller.admin;

import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author mryunqi
 * @date 2023/9/4
 */
@RestController
public class SysTestController {
    @Value("${config.admin_path}")
    private String ADMIN_PATH;

    /**
    * @Author: mryunqi
    * @Description: 测试通讯
    * @DateTime: 2023/9/4 20:53
    */
    @AdminApiCheck
    @GetMapping(value = "/{adminPath}/test")
    public Object test(@PathVariable("adminPath") String adminPath){
        if (!ADMIN_PATH.equals(adminPath)){
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        return ResponseResult.ok("通讯正常");
    }
}
