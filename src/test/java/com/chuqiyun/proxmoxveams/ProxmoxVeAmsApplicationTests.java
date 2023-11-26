package com.chuqiyun.proxmoxveams;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dto.VmParams;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.OsService;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.utils.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cglib.beans.BeanMap;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static com.chuqiyun.proxmoxveams.utils.ModUtil.formatFileSize;
import static com.chuqiyun.proxmoxveams.utils.ModUtil.getUrlFileSize;

@SpringBootTest
class ProxmoxVeAmsApplicationTests {
    @Resource
    private MasterService masterService;
    @Resource
    private OsService osService;


    //@Test
    void contextLoads() {

    }


    //@Test
    void pveApiTests() {
        int nodeId = 11;
        // 获取节点信息
        Master node = masterService.getById(nodeId);
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
        HashMap<String, Object> params = new HashMap<>();
        /*params.put("websocket", 1);
        // 设置generate-password
        params.put("generate-password", 1);
        // 获取指定节点的负载信息
        JSONObject nodeStatus = proxmoxApiUtil.postNodeApi(node, authentications, "/nodes/" + node.getNodeName() + "/qemu/128/vncproxy", params);
        JSONObject response = nodeStatus.getJSONObject("data");
        System.out.println(response);*/

        // 调用vncwebsocket接口
        JSONObject vncwebsocket = proxmoxApiUtil.getNodeApi(node, authentications, "/nodes/" + node.getNodeName() + "/network", params);
        System.out.println(vncwebsocket);
    }


}



