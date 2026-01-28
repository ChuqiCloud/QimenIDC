package com.chuqiyun.proxmoxveams.cron;

import com.chuqiyun.proxmoxveams.common.TimedLock;
import com.chuqiyun.proxmoxveams.common.VmCreateLock;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;

@Component
@EnableScheduling
@SpringBootApplication
public class VmLockCleaner {

    // 超过 1 分钟的锁就清理
    private static final long EXPIRE_TIME = 60_000;

    @Scheduled(fixedDelay = 30_000)
    public void cleanExpiredLocks() {
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<String, TimedLock>> it =
                VmCreateLock.getAllLocks().entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String, TimedLock> entry = it.next();
            TimedLock timedLock = entry.getValue();

            // 超时未使用，安全移除
            if (now - timedLock.getLastAccessTime() > EXPIRE_TIME) {
                it.remove();
            }
        }
    }
}
