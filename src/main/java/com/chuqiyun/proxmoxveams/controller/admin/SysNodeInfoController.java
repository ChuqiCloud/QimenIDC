package com.chuqiyun.proxmoxveams.controller.admin;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;

/**
 * @author mryunqi
 * @date 2023/8/5
 */
@RestController
@RequestMapping("/{adminPath}")
public class SysNodeInfoController {
    @Resource
    private MasterService masterService;

    @AdminApiCheck
    @GetMapping("/getNodeInfoByOne")
    public ResponseResult<Object> getNodeInfoByOne(@RequestParam(name = "nodeId") Integer nodeId) throws UnauthorizedException {
        // 获取node信息
        Master node = masterService.getById(nodeId);
        if (node == null){
            return ResponseResult.fail("节点不存在");
        }
        // 判断节点是否可用
        if (node.getStatus() != 0){
            return ResponseResult.fail("节点不可用");
        }
        HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        JSONObject nodeStatus = proxmoxApiUtil.getNodeStatusByOne(node, authentications);
        return ResponseResult.ok(nodeStatus.getJSONObject("data"));
    }

    @AdminApiCheck
    @GetMapping("/getNodeLoadAvg")
    public ResponseResult<Object> getNodeLoadAvg(@RequestParam(name = "nodeId") Integer nodeId,
                                                 @RequestParam(name = "timeframe" , defaultValue = "hour") String timeframe,
                                                 @RequestParam(name = "cf", defaultValue = "AVERAGE") String cf) throws UnauthorizedException {
        // 获取node信息
        Master node = masterService.getById(nodeId);
        if (node == null){
            return ResponseResult.fail("节点不存在");
        }
        // 判断节点是否可用
        if (node.getStatus() != 0){
            return ResponseResult.fail("节点不可用");
        }
        HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        JSONObject nodeStatus = proxmoxApiUtil.getNodeLoadAvg(node, authentications,timeframe,cf);
        return ResponseResult.ok(nodeStatus.getJSONArray("data"));
    }


}
