package com.chuqiyun.proxmoxveams.cron;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

/**
 * @author mryunqi
 * @date 2023/6/19
 */
@Component
@EnableScheduling
public class CookieUp {
    @Resource
    private MasterService masterService;

    /**
    * @Author: mryunqi
    * @Description: 每隔90分钟刷新一次token
    * @DateTime: 2023/6/19 21:58
    */
    @Async
    @Scheduled(fixedDelay = 1000*60*60)  //每隔60分钟执行一次
    public void cookieUpCron() throws IOException {
        QueryWrapper<Master> queryWrap = new QueryWrapper<>();
        queryWrap.eq("status",0);
        // 获取所有
        List<Master> nodeList = masterService.list(queryWrap);
        // 遍历
        for (Master node : nodeList) {
            String url = "https://"+node.getHost()+":"+node.getPort();
            HashMap<String,String> user = new HashMap<>();
            user.put("url",url);
            user.put("username",node.getUsername());
            user.put("password",node.getPassword());
            user.put("realm",node.getRealm());
            ProxmoxApiUtil pveApi = new ProxmoxApiUtil();
            HashMap<String, String> authentications = pveApi.loginAndGetCookie(user);
            node.setTicket(authentications.get("ticket"));
            node.setCsrfToken(authentications.get("csrfToken"));
            masterService.updateById(node);
        }
    }

}
