package com.chuqiyun.proxmoxveams.task.impl;

import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.task.TaskHandler;

import javax.annotation.Resource;

public class CreateDataDisk implements TaskHandler {

    @Resource
    private TaskService taskService;

    @Resource
    private MasterService masterService;

    @Resource
    private VmhostService vmhostService;
    @Override
    public void process(Task task) {

    }

    @Override
    public int getType() {
        return 0;
    }
}
