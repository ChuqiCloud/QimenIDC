package com.chuqiyun.proxmoxveams.cron;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
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
    @Scheduled(fixedRate = 1000*60*30)  //每隔30分钟执行一次
    public void cookieUpCron(){
        masterService.updateAllNodeCookie();
    }

    /**
    * @Author: mryunqi
    * @Description: 监控节点cookie是否过期，过期则更新
    * @DateTime: 2023/11/18 15:06
    */
    @Async
    @Scheduled(fixedRate = 1000*60)  //每隔1分钟执行一次
    public void cookieCheckCron(){
        int i = 1;
        while (true){
            QueryWrapper<Master> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("status",0);
            // 分页获取100行节点实例
            Page<Master> page = masterService.getMasterList(i,100,queryWrapper);
            List<Master> nodes = page.getRecords();
            // 如果获取到的节点实例为空，则跳出循环
            if (nodes.size() == 0){
                break;
            }
            // 遍历节点实例，更新cookie
            for (Master node : nodes) {
                // 获取节点cookie
                ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
                HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());

                JSONObject nodeStatus = null;
                try {
                    nodeStatus = proxmoxApiUtil.getNodeStatusByOne(node,authentications);
                } catch (Exception e) {
                    masterService.updateNodeCookie(node.getId());
                }
                // 如果获取到的节点状态为空，则重新获取cookie
                if (nodeStatus == null){
                    masterService.updateNodeCookie(node.getId());
                }

            }
            // 如果当前页数等于总页数则跳出循环
            if (i == page.getPages()){
                break;
            }
            i++;
        }
    }

}
