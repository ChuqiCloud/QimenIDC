package com.chuqiyun.proxmoxveams.cron;

import com.chuqiyun.proxmoxveams.service.VmResourceRankService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author codex
 * @date 2026/5/29
 */
@Slf4j
@Component
@EnableScheduling
public class VmResourceRankCron {
    @Resource
    private VmResourceRankService vmResourceRankService;

    @Async
    @Scheduled(fixedRate = 1000 * 60 * 60)
    public void refreshVmResourceRank() {
        try {
            vmResourceRankService.refreshRank();
        } catch (Exception e) {
            log.error("[VmResourceRankCron] refresh rank failed", e);
        }
    }
}
