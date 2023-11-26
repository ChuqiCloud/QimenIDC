package com.chuqiyun.proxmoxveams.controller.api.common;

import com.chuqiyun.proxmoxveams.annotation.PublicApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.utils.VmUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author mryunqi
 * @date 2023/11/26
 */
@RestController
@RequestMapping("/api/common")
public class VmPublicApi {

    /**
    * @Author: mryunqi
    * @Description: CPU类型
    * @DateTime: 2023/11/26 18:56
    */
    @PublicApiCheck
    @GetMapping("/cpuType")
    public ResponseResult<Object> cpuType() {
        return ResponseResult.ok(VmUtil.getCpuTypeMap());
    }

    /**
    * @Author: mryunqi
    * @Description: 获取OS类型
    * @DateTime: 2023/11/26 19:06
    */
    @PublicApiCheck
    @GetMapping("/osType")
    public ResponseResult<Object> osType() {
        return ResponseResult.ok(VmUtil.getOsTypeList());
    }

    /**
    * @Author: mryunqi
    * @Description: 获取OS架构列表
    * @DateTime: 2023/11/26 19:06
    */
    @PublicApiCheck
    @GetMapping("/osArch")
    public ResponseResult<Object> osArch() {
        return ResponseResult.ok(VmUtil.getArchList());
    }
}
