package com.chuqiyun.proxmoxveams.cron;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
import com.chuqiyun.proxmoxveams.constant.TaskType;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.OsService;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.utils.OsTypeUtil;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.chuqiyun.proxmoxveams.constant.TaskType.*;

/**
 * @author 星禾
 * @date 2025/12/08
 */
@Component
@EnableScheduling
public class ErrorTaskCron {
    private static final long APPLY_WINDOWS_VM_IP_TIMEOUT = 60 * 60 * 1000L;
    private static final long GENERAL_TASK_TIMEOUT = 10 * 60 * 1000L;
    private static final long CREATE_VM_TIMEOUT = 15 * 60 * 1000L;
    private static final long REINSTALL_VM_TIMEOUT = 15 * 60 * 1000L;

    @Resource
    private MasterService masterService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private TaskService taskService;
    @Resource
    private OsService osService;

    /**
     * 异常任务监控
     * 普通任务超过10分钟、创建和重装主任务超过15分钟仍处于执行中，则进行异常处理
     */
    @Async
    @Scheduled(fixedDelay = 60000)
    public void errorTaskCron() {
        recoverStuckCreateVmhosts();
        recoverStuckReinstallVmhosts();
        if (failTimeoutApplyWindowsVmIpTask()) {
            return;
        }
        QueryWrapper<Task> taskQueryWrapper = new QueryWrapper<>();
        long now = System.currentTimeMillis();
        taskQueryWrapper.eq("status", 1).and(wrapper -> wrapper
                .and(item -> item.ne("type", REINSTALL_VM).ne("type", CREATE_VM)
                        .apply("create_date <= {0}", now - GENERAL_TASK_TIMEOUT))
                .or(item -> item.eq("type", REINSTALL_VM)
                        .apply("create_date <= {0}", now - REINSTALL_VM_TIMEOUT))
                .or(item -> item.eq("type", CREATE_VM)
                        .apply("create_date <= {0}", now - CREATE_VM_TIMEOUT)));
        taskQueryWrapper.orderByAsc("create_date");
        Page<Task> taskPage = taskService.getTaskList(1, 1, taskQueryWrapper);
        // 判断是否没有任务
        if (taskPage.getRecords().isEmpty()) {
            return;
        }
        Task task = taskPage.getRecords().get(0);
        // 获取node信息
        Master node = masterService.getById(task.getNodeid());
        Vmhost vmhost = vmhostService.getById(task.getHostid());
        if (vmhost != null) {
            vmhost.setStatus(getRecoverStatus(task));
            vmhostService.updateById(vmhost);
        }
        // 更新主线程任务task状态为3
        task.setStatus(3);
        task.setError("异常任务监控处理超时");
        taskService.updateById(task);
        Integer nodeId = node == null ? task.getNodeid() : node.getId();
        UnifiedLogger.log(UnifiedLogger.LogType.TASK_RESET_SYSTEM, "异常任务状态监控处理完成: NodeID:{} VM-ID:{} TASK-TYPE:{}", nodeId, task.getVmid(),task.getType());
    }

    private Integer getRecoverStatus(Task task) {
        if (task == null) {
            return 1;
        }
        return getRecoverStatus(task.getNodeid(), task.getVmid());
    }

    private Integer getRecoverStatus(Integer nodeId, Integer vmid) {
        try {
            Integer pveStatus = masterService.getVmStatusCode(nodeId, vmid);
            if (pveStatus != null && pveStatus >= 0 && pveStatus <= 5) {
                return pveStatus;
            }
        } catch (Exception ignored) {
            // PVE 状态查询失败时默认恢复为关机，避免继续锁定虚拟机。
        }
        return 1;
    }

    private void recoverStuckCreateVmhosts() {
        QueryWrapper<Vmhost> vmQueryWrapper = new QueryWrapper<>();
        vmQueryWrapper.eq("status", 6);
        for (Vmhost vmhost : vmhostService.list(vmQueryWrapper)) {
            QueryWrapper<Task> taskQueryWrapper = new QueryWrapper<>();
            taskQueryWrapper.eq("hostid", vmhost.getId());
            taskQueryWrapper.eq("type", CREATE_VM);
            taskQueryWrapper.orderByDesc("create_date");
            taskQueryWrapper.last("LIMIT 1");
            Task latestTask = taskService.list(taskQueryWrapper).stream().findFirst().orElse(null);
            long taskAge = latestTask == null || latestTask.getCreateDate() == null
                    ? Long.MAX_VALUE : System.currentTimeMillis() - latestTask.getCreateDate();
            boolean activeTask = latestTask != null
                    && (Integer.valueOf(0).equals(latestTask.getStatus())
                    || Integer.valueOf(1).equals(latestTask.getStatus())
                    || Integer.valueOf(4).equals(latestTask.getStatus()));
            if (activeTask && taskAge < CREATE_VM_TIMEOUT) {
                continue;
            }
            if (activeTask) {
                latestTask.setStatus(3);
                latestTask.setError("创建虚拟机任务超时，自动恢复虚拟机状态");
                taskService.updateById(latestTask);
            }
            vmhost.setStatus(getRecoverStatus(vmhost.getNodeid(), vmhost.getVmid()));
            vmhostService.updateById(vmhost);
        }
    }

    private void recoverStuckReinstallVmhosts() {
        QueryWrapper<Vmhost> vmQueryWrapper = new QueryWrapper<>();
        vmQueryWrapper.eq("status", 13);
        for (Vmhost vmhost : vmhostService.list(vmQueryWrapper)) {
            QueryWrapper<Task> taskQueryWrapper = new QueryWrapper<>();
            taskQueryWrapper.eq("hostid", vmhost.getId());
            taskQueryWrapper.eq("type", REINSTALL_VM);
            taskQueryWrapper.orderByDesc("create_date");
            taskQueryWrapper.last("LIMIT 1");
            Task latestTask = taskService.list(taskQueryWrapper).stream().findFirst().orElse(null);
            long taskAge = latestTask == null || latestTask.getCreateDate() == null
                    ? Long.MAX_VALUE : System.currentTimeMillis() - latestTask.getCreateDate();
            boolean activeTask = latestTask != null
                    && (Integer.valueOf(0).equals(latestTask.getStatus())
                    || Integer.valueOf(1).equals(latestTask.getStatus()));
            if (activeTask && taskAge < REINSTALL_VM_TIMEOUT) {
                continue;
            }
            if (activeTask) {
                latestTask.setStatus(3);
                latestTask.setError("重装系统任务超时，自动恢复虚拟机状态");
                taskService.updateById(latestTask);
            }
            vmhost.setStatus(getRecoverStatus(vmhost.getNodeid(), vmhost.getVmid()));
            vmhostService.updateById(vmhost);
        }
    }

    private boolean failTimeoutApplyWindowsVmIpTask() {
        QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("type", APPLY_WINDOWS_VM_IP);
        queryWrapper.eq("status", 0);
        queryWrapper.apply("create_date <= {0}", System.currentTimeMillis() - APPLY_WINDOWS_VM_IP_TIMEOUT);
        queryWrapper.orderByAsc("create_date");
        Page<Task> taskPage = taskService.getTaskList(1, 1, queryWrapper);
        if (taskPage.getRecords().isEmpty()) {
            return false;
        }
        Task task = taskPage.getRecords().get(0);
        task.setStatus(3);
        task.setError("Windows附加IP应用超时，超过10分钟未执行成功");
        taskService.updateById(task);
        UnifiedLogger.log(UnifiedLogger.LogType.TASK_RESET_SYSTEM, "Windows附加IP应用任务超时失败: NodeID:{} VM-ID:{} TASK-ID:{}",
                task.getNodeid(), task.getVmid(), task.getId());
        return true;
    }
}
