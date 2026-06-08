package com.chuqiyun.proxmoxveams.cron;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import java.util.HashMap;
import java.util.Map;

import static com.chuqiyun.proxmoxveams.constant.TaskType.START_VM;
import static com.chuqiyun.proxmoxveams.constant.TaskType.UPDATE_VM_BOOT;

/**
 * @author mryunqi
 * @date 2023/7/1
 */
@Slf4j
@Component
@EnableScheduling
public class BootCron {
    private static final int UPDATE_BOOT_MAX_RETRY = 10;
    private static final long UPDATE_BOOT_RETRY_INTERVAL = 5000L;
    private static final String PVE_LOCK_TIMEOUT_MESSAGE = "can't lock file";

    @Resource
    private MasterService masterService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private TaskService taskService;

    @Async
    @Scheduled(fixedDelay = 2000)
    public void updateBootCron() {
        QueryWrapper<Task> queryWrap = new QueryWrapper<>();
        queryWrap.eq("type", UPDATE_VM_BOOT);
        queryWrap.eq("status", 0);
        Page<Task> taskPage = taskService.getTaskList(1,1,queryWrap);
        if (taskPage.getRecords().size() > 0){
            for (Task task: taskPage.getRecords()) {
                // 设置任务状态为1 1为正在执行
                task.setStatus(1);
                taskService.updateById(task);
                // 获取node信息
                Master node = masterService.getById(task.getNodeid());
                log.info("[Task-UpdateBoot] 执行修改启动项任务: NodeID:{} VM-ID:{}",node.getId(),task.getVmid());
                // 获取vm信息
                Vmhost vmhost = vmhostService.getById(task.getHostid());
                // 获取任务的参数
                Map<Object,Object> params = task.getParams();
                String boot = params.get("boot").toString();
                HashMap<String,Object> param = new HashMap<>();
                param.put("boot",boot);
                ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
                HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
                try {
                    updateBootWithRetry(proxmoxApiUtil, node, authentications, task, param);
                } catch (Exception e) {
                    log.error("[Task-UpdateBoot] 修改启动项失败: NodeID:{} VM-ID:{}",node.getId(),task.getVmid());
                    task.setStatus(3);
                    task.setError(e.getMessage());
                    taskService.updateById(task);
                    e.printStackTrace();
                    return;
                }
                log.info("[Task-UpdateBoot] 修改启动项成功: NodeID:{} VM-ID:{}",node.getId(),task.getVmid());
                // 设置任务状态为2 2为执行完成
                task.setStatus(2);
                taskService.updateById(task);
                // 创建开机任务
                /*Task startTask = new Task();
                long time = System.currentTimeMillis();
                startTask.setNodeid(node.getId());
                startTask.setVmid(task.getVmid());
                startTask.setHostid(vmhost.getId());
                startTask.setType(START_VM);
                startTask.setStatus(0);
                startTask.setCreateDate(time);
                if (taskService.insertTask(startTask)){
                    log.info("[Task-StartVm] 创建开机任务成功: NodeID:{} VM-ID:{}",node.getId(),task.getVmid());
                    // 增加虚拟机task
                    vmhost.getTask().put(String.valueOf(time), startTask.getId());
                    vmhostService.updateById(vmhost);
                }else {
                    log.error("[Task-StartVm] 创建开机任务失败: NodeID:{} VM-ID:{}",node.getId(),task.getVmid());
                    // 修改任务状态为失败
                    task.setStatus(3);
                    task.setError("创建修改启动项任务失败");
                    taskService.updateById(task);
                }*/
            }
        }
    }

    private void updateBootWithRetry(ProxmoxApiUtil proxmoxApiUtil, Master node,
                                     HashMap<String, String> authentications, Task task,
                                     HashMap<String, Object> param) throws Exception {
        for (int retry = 1; retry <= UPDATE_BOOT_MAX_RETRY; retry++) {
            try {
                proxmoxApiUtil.putNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+task.getVmid()+"/config", param);
                return;
            } catch (Exception e) {
                if (!isPveLockTimeout(e) || retry >= UPDATE_BOOT_MAX_RETRY) {
                    throw e;
                }
                log.warn("[Task-UpdateBoot] PVE配置锁占用，等待后重试: NodeID:{} VM-ID:{} Retry:{}/{}",
                        node.getId(), task.getVmid(), retry, UPDATE_BOOT_MAX_RETRY);
                sleepBeforeRetry();
            }
        }
    }

    private boolean isPveLockTimeout(Exception e) {
        String message = e.getMessage();
        return message != null && message.contains(PVE_LOCK_TIMEOUT_MESSAGE);
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(UPDATE_BOOT_RETRY_INTERVAL);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("修改启动项重试被中断", e);
        }
    }
}
