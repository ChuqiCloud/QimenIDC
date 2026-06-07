package com.chuqiyun.proxmoxveams.cron;

import com.chuqiyun.proxmoxveams.service.VmhostService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @Author: 星禾
 * @Description: 每日同步存量虚拟机防火墙和IP白名单
 * @DateTime: 2026/6/7 14:07
 */
@Slf4j
@Component
public class VmFirewallSyncCron {
    @Resource
    private VmhostService vmhostService;

    /**
     * @Author: 星禾
     * @Description: 自动同步已暂停，保留方法便于后续恢复
     * @DateTime: 2026/6/8 0:16
     */
    public void syncVmFirewallProtection() {
        try {
            boolean started = vmhostService.startSyncAllVmFirewallProtection();
            if (started) {
                log.info("[VmFirewallSyncCron] 每日虚拟机防火墙和IP白名单同步任务已提交");
            } else {
                log.info("[VmFirewallSyncCron] 已存在运行中的同步任务，跳过本次定时同步");
            }
        } catch (Exception e) {
            log.error("[VmFirewallSyncCron] 每日虚拟机防火墙和IP白名单同步任务提交失败", e);
        }
    }
}
