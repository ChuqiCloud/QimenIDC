package com.chuqiyun.proxmoxveams.manager;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.constant.TaskStatus;
import com.chuqiyun.proxmoxveams.constant.TaskType;
import com.chuqiyun.proxmoxveams.entity.Config;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.task.TaskHandler;
import com.chuqiyun.proxmoxveams.task.TaskHandlerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;

@Component
@EnableScheduling
public class TaskManager {

    @Resource
    private TaskService taskService;

    @Resource
    private TaskHandlerFactory taskHandlerFactory;

    @Resource
    private ConfigService configService;
    @Async
    @Scheduled(fixedDelay = 1000)
    public void processTasks() {QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", TaskStatus.WAITING);
        Page<Task> taskPage = taskService.getTaskList(1, 10, queryWrapper);
        List<Task> tasks = taskPage.getRecords();

        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        //获取全局配置信息
        Config config =  configService.getConfig();

        // 最大同时导入磁盘数量
        int maxImportDiskCount = 2;//config.getImportDiskMax();

        QueryWrapper<Task> importDiskQueryWrapper = new QueryWrapper<>();
        importDiskQueryWrapper.eq("type", TaskType.IMPORT_SYSTEM_DISK);
        importDiskQueryWrapper.eq("status", TaskStatus.RUNNING);

        for (Task task : tasks) {
            // 判断是否为导入磁盘任务
            if (Objects.equals(task.getType(), TaskType.IMPORT_SYSTEM_DISK)) {
                // 获取该任务相同nodeid的导入磁盘任务数量
                importDiskQueryWrapper.eq("nodeid", task.getNodeid());
                long count = taskService.count(importDiskQueryWrapper);
                if (count > maxImportDiskCount) {
                    continue;
                }
            }

            TaskHandler handler = taskHandlerFactory.getHandler(task.getType());
            if (handler != null) {
                handler.process(task);
            }
        }
    }


}
