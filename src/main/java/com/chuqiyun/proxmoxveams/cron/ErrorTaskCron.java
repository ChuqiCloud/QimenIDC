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
    private static final long APPLY_WINDOWS_VM_IP_TIMEOUT = 10 * 60 * 1000L;

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
     * 任务超过10分钟仍处于Status=1（执行中）状态，则进行异常处理
     */
    @Async
    @Scheduled(fixedDelay = 60000)
    public void errorTaskCron() {
        if (failTimeoutApplyWindowsVmIpTask()) {
            return;
        }
        QueryWrapper<Task> taskQueryWrapper = new QueryWrapper<>();
        taskQueryWrapper.eq("status", 1).apply("create_date <= {0}", System.currentTimeMillis() - 600_000);
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
            vmhost.setStatus(0);
            vmhostService.updateById(vmhost);
        }
        // 更新主线程任务task状态为3
        task.setStatus(3);
        task.setError("异常任务监控处理超时");
        taskService.updateById(task);
        Integer nodeId = node == null ? task.getNodeid() : node.getId();
        UnifiedLogger.log(UnifiedLogger.LogType.TASK_RESET_SYSTEM, "异常任务状态监控处理完成: NodeID:{} VM-ID:{} TASK-TYPE:{}", nodeId, task.getVmid(),task.getType());
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
