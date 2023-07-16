package com.chuqiyun.proxmoxveams;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.List;

@SpringBootTest
class ProxmoxVeAmsApplicationTests {

    @Test
    void contextLoads() {
        JSONObject context = ClientApiUtil.getControllerConnectStatus("1111111","dbb77f27239249c49bbf743a6b6063e31");
        System.out.println(context);
    }

}
