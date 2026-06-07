package com.chuqiyun.proxmoxveams.cron;

import com.chuqiyun.proxmoxveams.service.SystemLogService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @Author: 星禾
 * @Description: 系统日志定时清理任务
 * @DateTime: 2026/6/7 10:49
 */
@Component
@EnableScheduling
public class SystemLogCleanCron {
    @Resource
    private SystemLogService systemLogService;

    /**
     * @Author: 星禾
     * @Description: 每天凌晨清理过期系统日志
     * @DateTime: 2026/6/7 10:49
     */
    @Async
    @Scheduled(cron = "0 30 2 * * ?")
    public void cleanSystemLogCron() {
        systemLogService.deleteExpiredSystemLogs();
    }
}
