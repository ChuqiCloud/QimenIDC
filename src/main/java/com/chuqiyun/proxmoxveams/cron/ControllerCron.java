package com.chuqiyun.proxmoxveams.cron;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chuqiyun.proxmoxveams.entity.Config;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author mryunqi
 * @date 2023/7/11
 */
@Slf4j
@Component
@EnableScheduling
public class ControllerCron {
    @Resource
    private MasterService masterService;
    @Resource
    private ConfigService configService;

    @Async
    @Scheduled(fixedDelay = 1000*30)
    public void controllerStatusCron() {
        // 休眠10秒
        try {
            Thread.sleep(1000*10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Config config = configService.getById(1);
        if (config == null){
            return;
        }

        QueryWrapper<Master> queryWrap = new QueryWrapper<>();
        queryWrap.eq("status",0);
        // 获取所有
        List<Master> nodeList = masterService.list(queryWrap);
        for (Master node : nodeList){
            JSONObject result;
            try {
                result = ClientApiUtil.getControllerConnectStatus(node.getHost(),node.getControllerPort(),config.getToken());
            }catch (Exception e){
                log.error("获取控制器连接状态失败:["+e.getMessage()+"]");
                node.setControllerStatus(1);
                masterService.updateById(node);
                continue;
            }
            if (result == null){
                node.setControllerStatus(1);
                masterService.updateById(node);
                continue;
            }
            if (result.getInteger("code") != 200){
                node.setControllerStatus(1);
                masterService.updateById(node);
                continue;
            }
            node.setControllerStatus(0);
            masterService.updateById(node);
        }
    }

}
