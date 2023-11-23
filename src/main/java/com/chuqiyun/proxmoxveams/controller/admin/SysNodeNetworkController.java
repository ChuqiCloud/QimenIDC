package com.chuqiyun.proxmoxveams.controller.admin;

import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.PveNetworkService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
