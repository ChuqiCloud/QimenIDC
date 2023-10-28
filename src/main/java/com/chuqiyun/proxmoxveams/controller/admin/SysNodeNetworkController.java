package com.chuqiyun.proxmoxveams.controller.admin;

import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.PveNetworkService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author mryunqi
 * @date 2023/10/16
 */
@RestController
public class SysNodeNetworkController {
    @Value("${config.admin_path}")
    private String ADMIN_PATH;
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
    @GetMapping(value = "/{adminPath}/getPveNodeNetworkInfo")
    public Object getPveNodeNetworkInfo(@PathVariable("adminPath") String adminPath,
                                        Long nodeId) {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
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
    @GetMapping(value = "/{adminPath}/getPveNodeInterfaces")
    public Object getPveNodeInterfaces(@PathVariable("adminPath") String adminPath,
                                       Long nodeId) {
        if (!adminPath.equals(ADMIN_PATH)){
            //判断后台路径是否正确
            return ResponseResult.fail(ResponseResult.RespCode.NOT_PERMISSION);
        }
        // 判断nodeId是否存在
        if (masterService.getById(nodeId) == null) {
            return ResponseResult.fail("该节点不存在！");
        }
        return ResponseResult.ok(pveNetworkService.getPveInterfaces(nodeId));
    }
}
