package com.chuqiyun.proxmoxveams;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import com.chuqiyun.proxmoxveams.utils.ModUtil;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import com.chuqiyun.proxmoxveams.utils.UUIDUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;

import static com.chuqiyun.proxmoxveams.utils.ModUtil.formatFileSize;
import static com.chuqiyun.proxmoxveams.utils.ModUtil.getUrlFileSize;

@SpringBootTest
class ProxmoxVeAmsApplicationTests {
    @Resource
    private MasterService masterService;
    @Resource
    private SysuserService sysuserService;

    @Test
    void contextLoads() {
        long size = getUrlFileSize("http://oa.chuqiyun.com:8877/Cloud/Ubuntu/Ubuntu-22.04-x64.qcow2");
        //System.out.println(formatFileSize(size));
    }

    @Test
    void testRandomPassword() {
        String uuid = UUIDUtil.getUUIDByThreadString();
        String uuid2 = UUIDUtil.getUUIDByThreadString();
        System.out.println(uuid);
        System.out.println(uuid2);
    }
    @Test
    void pveApiTests() {
        int nodeId = 1;
        // 获取节点信息
        Master node = masterService.getById(nodeId);
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
        HashMap<String, Object> params = new HashMap<>();
//        params.put("timeframe", "hour"); // hour, day, week, month, year // 采样时间
//        params.put("cf", "AVERAGE"); // AVERAGE, MAX, MIN or LAST   // 采样方式
        // 获取指定节点的负载信息
        /*JSONObject nodeStatus = proxmoxApiUtil.getNodeApi(node, authentications, "/nodes/" + node.getNodeName() + "/rrddata?timeframe=hour&cf=AVERAGE", params);
        System.out.println(nodeStatus);*/
    }


}
