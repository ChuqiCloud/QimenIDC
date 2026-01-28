package com.chuqiyun.proxmoxveams.common;

import java.util.concurrent.locks.ReentrantLock;

public class TimedLock {

    private final ReentrantLock lock = new ReentrantLock();
    private volatile long lastAccessTime;

    public TimedLock() {
        touch();
    }

    public ReentrantLock getLock() {
        touch();
        return lock;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public void touch() {
        this.lastAccessTime = System.currentTimeMillis();
    }

    public boolean isLocked() {
        return lock.isLocked();
    }
}

