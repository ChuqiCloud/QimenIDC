package com.chuqiyun.proxmoxveams;

import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.List;

@SpringBootTest
class ProxmoxVeAmsApplicationTests {

    @Test
    void contextLoads() {
        System.out.println(ClientApiUtil.getNetOs("https://gitee.com/chuqicloud/soft-ware/raw/master/Cloud/os.json"));
    }

}
