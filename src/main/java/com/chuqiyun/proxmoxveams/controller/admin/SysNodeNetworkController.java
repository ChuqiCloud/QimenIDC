package com.chuqiyun.proxmoxveams.controller.admin;

import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.dto.NetWorkParams;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.PveNetworkService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * @author mryunqi
 * @date 2023/10/16
 */
@RestController
@RequestMapping("/{adminPath}")
public class SysNodeNetworkController {
    @Resource
    private MasterService masterService;
    @Resource
    private PveNetworkService pveNetworkService;

    /**
    * @Author: mryunqi
    * @Description: 获取节点网络信息
    * @DateTime: 2023/10/16 20:51
    */
    @AdminApiCheck
    @GetMapping(value = "/getPveNodeNetworkInfo")
    public Object getPveNodeNetworkInfo(Long nodeId) {
        // 判断nodeId是否存在
        if (masterService.getById(nodeId) == null) {
            return ResponseResult.fail("该节点不存在！");
        }
        return ResponseResult.ok(pveNetworkService.getPveNetworkInfo(nodeId));
    }

    /**
    * @Author: mryunqi
    * @Description: 获取节点网卡配置文件信息
    * @DateTime: 2023/10/28 22:10
    */
    @AdminApiCheck
    @GetMapping(value = "/getPveNodeInterfaces")
    public Object getPveNodeInterfaces(Long nodeId) {
        // 判断nodeId是否存在
        if (masterService.getById(nodeId) == null) {
            return ResponseResult.fail("该节点不存在！");
        }
        return ResponseResult.ok(pveNetworkService.getPveInterfaces(nodeId));
    }

    /**
    * @Author: mryunqi
    * @Description: 创建虚拟网卡
    * @DateTime: 2024/1/17 21:34
    */
    @AdminApiCheck
    @PostMapping(value = "/createPveNodeInterface/{nodeId}")
    public ResponseResult<Object> createPveNodeInterface(@PathVariable("nodeId") Long nodeId,
                                                         @RequestBody NetWorkParams netWorkParams) throws UnauthorizedException {
        UnifiedResultDto<Object> resultDto = pveNetworkService.createNetWork(nodeId,netWorkParams);
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return ResponseResult.fail(resultDto.getResultCode().getCode(),resultDto.getResultCode().getMessage());
        }
        return ResponseResult.ok(resultDto.getResultCode().getMessage());
    }
}
