package com.chuqiyun.proxmoxveams.common;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;


public final class VmCreateLock {

    private VmCreateLock() {}

    // hostname -> TimedLock
    private static final ConcurrentHashMap<String, TimedLock> LOCK_MAP =
            new ConcurrentHashMap<>();

    public static TimedLock getTimedLock(String hostname) {
        return LOCK_MAP.computeIfAbsent(hostname, k -> new TimedLock());
    }

    public static ConcurrentHashMap<String, TimedLock> getAllLocks() {
        return LOCK_MAP;
    }
}
