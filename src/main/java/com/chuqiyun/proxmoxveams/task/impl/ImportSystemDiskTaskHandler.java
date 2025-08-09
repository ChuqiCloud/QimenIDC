package com.chuqiyun.proxmoxveams.task.impl;

import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
import com.chuqiyun.proxmoxveams.constant.TaskStatus;
import com.chuqiyun.proxmoxveams.constant.TaskType;
import com.chuqiyun.proxmoxveams.dto.VmParams;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Os;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.OsService;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.task.TaskHandler;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Component
public class ImportSystemDiskTaskHandler implements TaskHandler {

    @Resource
    private TaskService taskService;

    @Resource
    private MasterService masterService;

    @Resource
    private VmhostService vmhostService;

    @Resource
    private OsService osService;

    @Resource
    private ConfigService configService;

    @Override
    public void process(Task task) {
        // 更新任务状态为1 (正在执行)
        task.setStatus(TaskStatus.RUNNING);
        taskService.updateById(task);

        // 获取node信息
        Master node = masterService.getById(task.getNodeid());
        // 获取vm信息
        Vmhost vmhost = vmhostService.getById(task.getHostid());
        String os = vmhost.getOs();
        Os osInfo = osService.selectOsByFileName(os);
        String path = osInfo.getPath();
        // 删除结尾的/
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        UnifiedLogger.log(UnifiedLogger.LogType.TASK_IMPORT_SYSTEM_DISK,"执行导入系统盘任务: NodeID:{} VM-ID:{}",node.getId(),task.getVmid());

        try {

            // boolean result = ClientApiUtil.importDisk(node.getHost(), node.getControllerPort(), configService.getToken(), Long.valueOf(task.getVmid()), path + "/" + os, vmhost.getStorage());

            if (!result) {
                task.setStatus(TaskStatus.FAILURE);
                task.setError("导入系统盘失败");
                taskService.updateById(task);
            } else {
                task.setStatus(TaskStatus.SUCCESS);
                taskService.updateById(task);
            }
        } catch (Exception e) {
            UnifiedLogger.warn(UnifiedLogger.LogType.TASK_IMPORT_SYSTEM_DISK,"导入系统盘任务: NodeID:{} VM-ID:{} 失败",node.getId(),task.getVmid());
            task.setStatus(TaskStatus.FAILURE);
            task.setError(e.getMessage());
            taskService.updateById(task);
            e.printStackTrace();
        }
        // 添加新导入磁盘配置任务
        addSubTasks(task,node.getId(),vmhost.getVmid(),vmhost.getId());
    }

    private void addSubTasks(Task task,Integer nodeid, Integer vmIdInit, Integer vmhostId) {
        // 添加新导入磁盘配置任务
        Task importTaskNew = new Task();
        importTaskNew.setType(TaskType.IMPORT_SYSTEM_DISK_NEW);
        importTaskNew.setVmid(vmIdInit);
        importTaskNew.setNodeid(nodeid);
        importTaskNew.setHostid(vmhostId);
        importTaskNew.setMasterId(task.getMasterId());
        importTaskNew.setStatus(TaskStatus.WAITING);
        importTaskNew.setCreateDate(System.currentTimeMillis());
        importTaskNew.setParams(task.getParams());
        taskService.insertTask(importTaskNew);

    }

    @Override
    public int getType() {
        return TaskType.IMPORT_SYSTEM_DISK;
    }
}
