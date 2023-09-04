package com.chuqiyun.proxmoxveams.cron;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import java.util.HashMap;

import static com.chuqiyun.proxmoxveams.constant.TaskType.DELETE_VM;
import static com.chuqiyun.proxmoxveams.constant.TaskType.RESET_PASSWORD;

/**
 * @author mryunqi
 * @date 2023/9/2
 */
@Component
@EnableScheduling
public class DeleteVmCron {
    @Resource
    private MasterService masterService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private TaskService taskService;

    /**
    * @Author: mryunqi
    * @Description: 删除虚拟机
    * @DateTime: 2023/9/2 16:07
    */
    @Async
    @Scheduled(fixedDelay = 1000)
    public void deleteVm() {
        QueryWrapper<Task> taskQueryWrapper = new QueryWrapper<>();
        taskQueryWrapper.eq("type", DELETE_VM);
        taskQueryWrapper.eq("status", 0);
        Page<Task> taskPage = taskService.getTaskList(1, 1, taskQueryWrapper);
        // 判断是否没有任务
        if (taskPage.getRecords().size() == 0) {
            return;
        }
        Task task = taskPage.getRecords().get(0);
        // 修改任务状态为1
        task.setStatus(1);
        taskService.updateById(task);
        // 获取node信息
        Master node = masterService.getById(task.getNodeid());
        HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        proxmoxApiUtil.deleteVm(node, authentications, task.getVmid());
        // 删除数据库中的虚拟机信息
        vmhostService.removeById(task.getHostid());
        // 修改任务状态为2
        task.setStatus(2);
        taskService.updateById(task);
    }
}
