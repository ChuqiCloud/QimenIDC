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
    // 与 Controlled/common/Version.py 的 API_VERSION 保持一致，独立于两端程序版本。
    private static final String EXPECTED_AGENT_API_VERSION = "1.0.1";

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
     * 主控程序版本，仅用于排查部署版本，不参与被控接口兼容性判断
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
    * @Description: 按接口协议版本检查兼容性，程序版本仅作诊断。
     * 读写路径类接口已下线，加密负载是被控端API的唯一约定，不再做运行时能力协商，
     * 避免协商结果被中间人利用降级到明文。
    */
    private void syncAgentSecurity(Master node, String token) {
        JSONObject versionInfo = ClientApiUtil.getAgentVersionInfo(node.getHost(), node.getControllerPort(), token);
        ClientPayloadUtil.setEncryptEnabled(true);
        reportAgentCompatibility(node, versionInfo);
    }

    private void reportAgentCompatibility(Master node, JSONObject versionInfo) {
        if (versionInfo == null) {
            log.warn("无法获取节点[{}]被控端版本信息: nodeId={}", node.getName(), node.getId());
            return;
        }
        String apiVersion = versionInfo.getString("api_version");
        String agentVersion = versionInfo.getString("version");
        if (!Objects.equals(apiVersion, EXPECTED_AGENT_API_VERSION)) {
            log.warn("节点[{}]被控端接口协议不兼容或未声明: 预期协议={}, 被控协议={}, 被控程序={}, nodeId={}",
                    node.getName(), EXPECTED_AGENT_API_VERSION, apiVersion, agentVersion, node.getId());
            return;
        }
        log.debug("节点[{}]接口协议兼容: 协议={}, 主控程序={}, 被控程序={}, nodeId={}",
                node.getName(), apiVersion, masterVersion, agentVersion, node.getId());
    }

}
