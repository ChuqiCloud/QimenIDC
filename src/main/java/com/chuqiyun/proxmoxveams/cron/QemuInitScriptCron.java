package com.chuqiyun.proxmoxveams.cron;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmInitScriptBusinessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import static com.chuqiyun.proxmoxveams.constant.TaskType.RUN_QEMU_INIT_SCRIPT;

/**
 * @Author: 鏄熺
 * @Description: QEMU Guest Agent初始化脚本任务
 * @DateTime: 2026/7/3 20:47
 */
@Slf4j
@Component
@EnableScheduling
public class QemuInitScriptCron {
    @Resource
    private TaskService taskService;
    @Resource
    private VmInitScriptBusinessService vmInitScriptBusinessService;

    @Async
    @Scheduled(fixedDelay = 2000)
    public void runQemuInitScript() {
        QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("type", RUN_QEMU_INIT_SCRIPT);
        queryWrapper.eq("status", 0);
        Page<Task> taskPage = taskService.getTaskList(1, 1, queryWrapper);
        if (taskPage.getRecords().isEmpty()) {
            return;
        }
        Task task = taskPage.getRecords().get(0);
        task.setStatus(1);
        taskService.updateById(task);
        try {
            boolean success = vmInitScriptBusinessService.executeTask(task);
            task.setStatus(success ? 2 : 3);
            if (!success) {
                task.setError("QEMU初始化脚本执行失败，请查看vm_init_script_record");
            }
            taskService.updateById(task);
        } catch (Exception e) {
            log.error("[Task-QemuInitScript] 执行QEMU初始化脚本失败: taskId={}", task.getId(), e);
            task.setStatus(3);
            task.setError(e.getMessage());
            taskService.updateById(task);
        }
    }
}
