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

import static com.chuqiyun.proxmoxveams.constant.TaskType.START_VM;

/**
 * @author mryunqi
 * @date 2023/7/1
 */
@Slf4j
@Component
@EnableScheduling
public class StartVmCron {
    @Resource
    private MasterService masterService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private TaskService taskService;

    @Async
    @Scheduled(fixedDelay = 1000)
    public void startVm() {
        QueryWrapper<Task> queryWrap = new QueryWrapper<>();
        queryWrap.eq("type", START_VM);
        queryWrap.eq("status", 0);
        Page<Task> taskPage = taskService.getTaskList(1,2,queryWrap);
        if (taskPage.getRecords().size() > 0){
            for (Task task: taskPage.getRecords()) {
                // 设置任务状态为1 1为正在执行
                task.setStatus(1);
                taskService.updateById(task);
                // 获取node信息
                Master node = masterService.getById(task.getNodeid());
                log.info("[Task-StartVm] 执行开机任务: NodeID:{} VM-ID:{}",node.getId(),task.getVmid());
                // 获取vm信息
                Vmhost vmhost = vmhostService.getById(task.getHostid());
                ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
                HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
                HashMap<String,Object> params = new HashMap<>();
                try {
                    proxmoxApiUtil.postNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+task.getVmid()+"/status/start", params);
                } catch (Exception e) {
                    log.error("[Task-StartVm] 开机任务: NodeID:{} VM-ID:{} 失败",node.getId(),task.getVmid());
                    // 修改任务状态为3 3为执行失败
                    task.setStatus(3);
                    task.setError(e.getMessage());
                    taskService.updateById(task);
                    e.printStackTrace();
                    continue;
                }
                // 设置任务状态为2 2为执行完成
                task.setStatus(2);
                taskService.updateById(task);
                log.info("[Task-StartVm] 开机任务: NodeID:{} VM-ID:{} 完成",node.getId(),task.getVmid());
            }
        }
    }
}
