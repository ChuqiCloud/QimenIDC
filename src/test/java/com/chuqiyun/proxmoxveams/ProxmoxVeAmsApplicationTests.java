package com.chuqiyun.proxmoxveams;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;

@SpringBootTest
class ProxmoxVeAmsApplicationTests {
    @Resource
    private MasterService masterService;

    @Test
    void contextLoads() {
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        Master node = masterService.getById(1);
        // 获取cookie
        HashMap<String, String> authentications = masterService.getMasterCookieMap(1);
        JSONObject data = proxmoxApiUtil.getNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/", new HashMap<>());
        System.out.println(data);
    }

}
