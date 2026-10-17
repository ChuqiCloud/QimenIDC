package com.chuqiyun.proxmoxveams.controller.admin;

import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.manager.TrustedNetworkManager;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 管理端查看被控端可信来源白名单。
 *
 * <p>被控端 nginx 只放行白名单里的管理源地址，主控换了出口 IP 又没同步时会以
 * 「连接超时」的方式整体失联，排查时最先要确认的就是这份列表。</p>
 *
 * @author CodeBuddy
 * @date 2026/10/17
 */
@RestController
@RequestMapping("/{adminPath}")
public class SysWhitelistController {
    @Resource
    private TrustedNetworkManager trustedNetworkManager;

    @ApiOperation(value = "查看被控端可信来源白名单", notes = "返回集群标识、主控自报出口地址、节点实测到的地址，以及同步成功与待重试的节点")
    @AdminApiCheck
    @GetMapping(value = "/getAgentWhitelist")
    public ResponseResult<Object> getAgentWhitelist() throws UnauthorizedException {
        return ResponseResult.ok(trustedNetworkManager.describe());
    }
}
