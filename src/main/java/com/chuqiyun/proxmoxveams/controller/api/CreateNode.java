package com.chuqiyun.proxmoxveams.controller.api;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.annotation.PublicSysApiCheck;
import com.chuqiyun.proxmoxveams.entity.*;
import com.chuqiyun.proxmoxveams.service.*;
import com.chuqiyun.proxmoxveams.utils.EntityHashMapConverterUtil;
import com.chuqiyun.proxmoxveams.utils.ModUtil;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import com.chuqiyun.proxmoxveams.utils.ResponseResult;
import com.chuqiyun.proxmoxveams.utils.exception.UnauthorizedException;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;

import static com.chuqiyun.proxmoxveams.constant.TaskType.CREATE_VM;

/**
 * @author mryunqi
 * @date 2023/6/18
 */
@Slf4j
@RestController
public class CreateNode {
    @Resource
    private MasterService masterService;
    @Resource
    private TaskService taskService;
    @Resource
    private IpstatusService ipstatusService;
    @Resource
    private IppoolService ippoolService;
    @ApiOperation(value = "创建虚拟机", notes = "创建虚拟机")
    @PublicSysApiCheck
    @PostMapping("/api/cerateVM")
    public ResponseResult<String> createVm(@RequestBody VmParams vmParams) throws UnauthorizedException {
        int nodeId = vmParams.getNodeid();
        Master node = masterService.getById(nodeId);
        // 判断实体类是否为空
        if (ModUtil.isNull(node)) {
            return ResponseResult.fail("节点不存在");
        }
        // 判断节点是否可用
        if (node.getStatus() >= 1) {
            return ResponseResult.fail("节点不可用");
        }
        // 判断nested是否为空
        if (vmParams.getNested() == null) {
            vmParams.setNested(false);
        }
        // 判断sockets是否为空
        if (vmParams.getSockets() == null) {
            vmParams.setSockets(1);
        } else if (vmParams.getSockets() < 1) {
            vmParams.setSockets(1);
        }
        if (vmParams.getStorage() == null){
            vmParams.setStorage("local-lvm");
        }else if ("auto".equals(vmParams.getStorage())) {
            vmParams.setStorage(node.getAutoStorage());
        }
        // 设置网络
        if (vmParams.getBridge() == null) {
            vmParams.setBridge("vmbr0");
        }
        // 获取可用ip最多的ip池
        Ipstatus ipPool = ipstatusService.getIpStatusMaxByNodeId(nodeId);
        if (vmParams.getIpConfig() == null || vmParams.getIpConfig().size() < 1){
            HashMap<String,String> ipConfig = new HashMap<>();
            if (ipPool == null) {
                return ResponseResult.fail("没有可用的IPV4资源");
            }
            if (ipPool.getAvailable() == 0) {
                return ResponseResult.fail("没有可用的IPV4资源");
            }
            // 获取可用IP
            Ippool ipEntity = ippoolService.getOneOkIpByPoolId(ipPool.getId());
            if (ipEntity == null) {
                return ResponseResult.fail("没有可用的IPV4资源");
            }
            ipConfig.put("1","ip="+ipEntity.getIp()+"/"+ipPool.getMask()+",gw="+ipEntity.getGateway());
            vmParams.setIpConfig(ipConfig);
        }else {
            int count = vmParams.getIpConfig().size();
            HashMap<String, String> ipConfigMap = vmParams.getIpConfig();
            for (int i = 1; i <= count; i++) {
                String ipConfig = ipConfigMap.get(i);
                if (ipConfig == null) {
                    if (ipPool == null) {
                        return ResponseResult.fail("没有可用的IPV4资源");
                    }
                    if (ipPool.getAvailable() == 0) {
                        return ResponseResult.fail("没有可用的IPV4资源");
                    }
                    // 获取可用IP
                    Ippool ipEntity = ippoolService.getOneOkIpByPoolId(ipPool.getId());
                    if (ipEntity == null) {
                        return ResponseResult.fail("没有可用的IPV4资源");
                    }
                    // 修改vmParams
                    ipConfigMap.put(String.valueOf(i), "ip=" + ipEntity.getIp() + "/" + ipPool.getMask() + ",gw=" + ipEntity.getGateway());
                }
            }
            vmParams.setIpConfig(ipConfigMap);
        }
        // 设置dns
        vmParams.setDns1(ipPool.getDns1());
        HashMap<Object, Object> vmParamsMap;
        // 将vmParams转换为HashMap
        try {
            vmParamsMap = EntityHashMapConverterUtil.convertToHashMap(vmParams);
        } catch (IllegalAccessException e) {
            log.warn("[API] 创建虚拟机任务参数转换失败");
            e.printStackTrace();
            return ResponseResult.ok("创建虚拟机失败");
        }
        log.info("[API] 创建基础虚拟机任务: NodeID="+nodeId+",OsType="+vmParams.getOsType()+
                ",Sockets="+vmParams.getSockets()+",Cores="+vmParams.getCores()+",Memory="+vmParams.getMemory());
        // 创建虚拟机任务
        Task task = new Task();
        task.setStatus(0);
        task.setType(CREATE_VM);
        task.setParams(vmParamsMap);
        task.setCreateDate(System.currentTimeMillis());
        if (taskService.insertTask(task)){
            log.info("[API] 创建虚拟机任务: NodeID="+nodeId+",OsType="+vmParams.getOsType()+
                    ",Sockets="+vmParams.getSockets()+",Cores="+vmParams.getCores()+",Memory="+vmParams.getMemory()+" 完成");
            return ResponseResult.ok("创建虚拟机成功");
        }
        log.warn("[API 创建基础虚拟机任务失败");
        return ResponseResult.ok("创建虚拟机失败");
    }

    @PublicSysApiCheck
    @GetMapping("/api/getNode")
    public ResponseResult getNode() throws UnauthorizedException {
        Master node = masterService.getById(5);
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        // 获取cookie
        HashMap<String, String> authentications = masterService.getMasterCookieMap(5);
        HashMap<String, Object> param = new HashMap<>();
        // 添加一个数据盘
        //param.put("scsi1", "local-lvm:10");
        JSONObject json = proxmoxApiUtil.getNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/storage", param);
        //JSONObject json =  proxmoxApiService.getNodeApi(node,authentications, "/nodes/pve/qemu/101/config", param);
        return ResponseResult.ok(json);
    }
}
