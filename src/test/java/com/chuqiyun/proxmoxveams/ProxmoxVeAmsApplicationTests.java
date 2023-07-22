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

import static com.chuqiyun.proxmoxveams.utils.ModUtil.formatFileSize;
import static com.chuqiyun.proxmoxveams.utils.ModUtil.getUrlFileSize;

@SpringBootTest
class ProxmoxVeAmsApplicationTests {
    @Resource
    private MasterService masterService;

    @Test
    void contextLoads() {
        long size = getUrlFileSize("http://oa.chuqiyun.com:8877/Cloud/Ubuntu/Ubuntu-22.04-x64.qcow2");
        System.out.println(formatFileSize(size));
    }

}
