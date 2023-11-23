package com.chuqiyun.proxmoxveams.controller.admin;

import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author mryunqi
 * @date 2023/9/4
 */
@RestController
@RequestMapping("/{adminPath}")
public class SysTestController {

    /**
    * @Author: mryunqi
    * @Description: 测试通讯
    * @DateTime: 2023/9/4 20:53
    */
    @AdminApiCheck
    @GetMapping(value = "/test")
    public Object test(){
        return ResponseResult.ok("通讯正常");
    }
}
