package com.chuqiyun.proxmoxveams.cron;

import com.chuqiyun.proxmoxveams.service.VmhostService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author: 星禾
 * @Description: 每日同步存量虚拟机防火墙和IP白名单
 * @DateTime: 2026/6/7 14:07
 */
@Slf4j
@Component
@EnableScheduling
public class VmFirewallSyncCron {
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Resource
    private VmhostService vmhostService;

    @Async
    @Scheduled(cron = "0 20 3 * * ?")
    public void syncVmFirewallProtection() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            log.info("[VmFirewallSyncCron] 开始每日虚拟机防火墙和IP白名单同步");
            vmhostService.syncAllVmFirewallProtection();
            log.info("[VmFirewallSyncCron] 每日虚拟机防火墙和IP白名单同步完成");
        } catch (Exception e) {
            log.error("[VmFirewallSyncCron] 每日虚拟机防火墙和IP白名单同步失败", e);
        } finally {
            running.set(false);
        }
    }
}
