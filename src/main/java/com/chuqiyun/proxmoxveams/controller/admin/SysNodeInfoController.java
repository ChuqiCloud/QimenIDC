package com.chuqiyun.proxmoxveams.controller.admin;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;

/**
 * @author mryunqi
 * @date 2023/8/5
 */
@RestController
public class SysNodeInfoController {
    @Value("${config.admin_path}")
    private String ADMIN_PATH;
    @Resource
    private MasterService masterService;

    @AdminApiCheck
    @GetMapping("/{adminPath}/getNodeInfoByOne")
    public ResponseResult<Object> getNodeInfoByOne(@PathVariable("adminPath") String adminPath,
                                                   @RequestParam(name = "nodeId") Integer nodeId) throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
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
    @GetMapping("/{adminPath}/getNodeLoadAvg")
    public ResponseResult<Object> getNodeLoadAvg(@PathVariable("adminPath") String adminPath,
                                                 @RequestParam(name = "nodeId") Integer nodeId,
                                                 @RequestParam(name = "timeframe" , defaultValue = "hour") String timeframe,
                                                 @RequestParam(name = "cf", defaultValue = "AVERAGE") String cf) throws UnauthorizedException {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
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
