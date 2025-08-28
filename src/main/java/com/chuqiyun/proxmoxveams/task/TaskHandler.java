package com.chuqiyun.proxmoxveams.task;

import com.chuqiyun.proxmoxveams.entity.Task;

public interface TaskHandler {
    void process(Task task);
    int getType();
}
