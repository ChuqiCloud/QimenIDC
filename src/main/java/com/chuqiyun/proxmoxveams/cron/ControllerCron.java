package com.chuqiyun.proxmoxveams.cron;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chuqiyun.proxmoxveams.entity.Config;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.manager.TrustedNetworkManager;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import com.chuqiyun.proxmoxveams.utils.ClientPayloadUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;

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
    /**
     * 管理源白名单同步，心跳顺带登记被控端实测到的对端地址并补发未完成的节点
     */
    @Resource
    private TrustedNetworkManager trustedNetworkManager;
    /**
     * 主控版本，用于与被控端做一致性校验
     */
    @Value("${info.application.version}")
    private String masterVersion;

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
        queryWrap.ne("status",2);
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
            trustedNetworkManager.observeAgentReport(node.getId(), result);
            syncAgentSecurity(node, config.getToken());
            if (Objects.equals(node.getStatus(), 1)) {
                masterService.updateNodeCookie(node.getId());
            }
        }
        try {
            trustedNetworkManager.retryPending();
        } catch (Exception e) {
            log.warn("可信白名单补偿同步失败: {}", e.getMessage());
        }
    }

    /**
    * @Author: 星禾
    * @Description: 读取被控端版本，仅用于版本不一致时告警。
     * 读写路径类接口已下线，加密负载是被控端API的唯一约定，不再做运行时能力协商，
     * 避免协商结果被中间人利用降级到明文。
    */
    private void syncAgentSecurity(Master node, String token) {
        String agentVersion = ClientApiUtil.getAgentVersion(node.getHost(), node.getControllerPort(), token);
        if (agentVersion == null) {
            log.warn("无法获取节点[{}]被控端版本信息: nodeId={}", node.getName(), node.getId());
            return;
        }
        ClientPayloadUtil.setEncryptEnabled(true);
        if (!Objects.equals(agentVersion, masterVersion)) {
            log.warn("节点[{}]被控端版本与主控不一致: 主控={}, 被控={}, nodeId={}",
                    node.getName(), masterVersion, agentVersion, node.getId());
        }
    }

}
