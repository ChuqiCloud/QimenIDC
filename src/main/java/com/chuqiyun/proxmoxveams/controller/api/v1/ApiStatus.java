package com.chuqiyun.proxmoxveams.controller.api.v1;

import com.chuqiyun.proxmoxveams.annotation.PublicSysApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author mryunqi
 * @date 2023/9/24
 */
@RestController
@RequestMapping("/api/v1")
public class ApiStatus {
    @PublicSysApiCheck
    @GetMapping("/status")
    public ResponseResult<Object> status() {
        return ResponseResult.ok("通讯正常");
    }
}
