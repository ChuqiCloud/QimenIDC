package com.chuqiyun.proxmoxveams.cron;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.ProxmoxApiService;
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
    @Resource
    private MasterService masterService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private TaskService taskService;

    @Async
    @Scheduled(fixedDelay = 1000)
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
                ProxmoxApiService proxmoxApiService = new ProxmoxApiService();
                HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
                try {
                    proxmoxApiService.putNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+task.getVmid()+"/config", param);
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
                Task startTask = new Task();
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
                }
            }
        }
    }
}
