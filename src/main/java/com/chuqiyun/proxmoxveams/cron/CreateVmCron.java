package com.chuqiyun.proxmoxveams.cron;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.constant.TaskType;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.dto.VmParams;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.utils.EntityHashMapConverterUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * @author mryunqi
 * @date 2023/6/30
 */
@Slf4j
@Component
@EnableScheduling
public class CreateVmCron {
    @Resource
    private MasterService masterService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private TaskService taskService;

    /**
     * 创建虚拟机
     */
    @Async
    @Scheduled(fixedDelay = 500)
    public void createVm() {
        // 获取TaskType为CREATE_VM的任务列表
        QueryWrapper<Task> queryWrap = new QueryWrapper<>();
        queryWrap.eq("type", TaskType.CREATE_VM);
        queryWrap.eq("status", 0);
        // 只获取一个
        Page<Task> taskPage = taskService.getTaskList(1,1,queryWrap);
        // 判断是否有任务
        if (taskPage.getRecords().size() > 0){
            // 获取任务
            Task task = taskPage.getRecords().get(0);
            // 设置任务状态为1 1为正在执行
            task.setStatus(1);
            taskService.updateById(task);
            // 获取任务的参数
            Map<Object,Object> params = task.getParams();
            // 转换为VmParams
            VmParams vmParams;
            try {
                vmParams = EntityHashMapConverterUtil.convertToEntity(params, VmParams.class);
            } catch (IllegalAccessException | InstantiationException e) {
                log.error("[Task-CreateVm] 创建虚拟机任务参数转换失败");
                // 更新任务状态为3 3为失败
                task.setStatus(3);
                // 记录错误信息
                task.setError("创建虚拟机任务参数转换失败");
                taskService.updateById(task);
                e.printStackTrace();
                // 结束任务
                return;
            }
            int vmId = masterService.createVm(vmParams);
            // 判断是否创建成功
            if (vmId == 0) {
                // 创建失败，修改任务状态为3
                task.setStatus(3);
                // 记录错误信息
                task.setError("创建基础虚拟机失败");
                taskService.updateById(task);
                // 结束任务
                return;
            }
            // 记录虚拟机id
            task.setVmid(vmId);
            // 记录节点id
            task.setNodeid(vmParams.getNodeid());
            taskService.updateById(task);
            log.info("[Task-CreateVm] 创建基础虚拟机成功");
            String createTime = String.valueOf(System.currentTimeMillis());
            HashMap<Object, Object> taskMap = new HashMap<>();
            taskMap.put(createTime, task.getId());
            vmParams.setTask(taskMap);
            // 将创建的虚拟机信息存入数据库
            Integer vmhostId = vmhostService.addVmhost(vmId, vmParams);
            // 判断是否存入成功
            if (vmhostId == null) {
                // 存入失败，修改任务状态为3
                task.setStatus(3);
                // 记录错误信息
                task.setError("创建虚拟机信息存入数据库失败");
                taskService.updateById(task);
                // 结束任务
                return;
            }
            // 更新当前任务hostid
            task.setHostid(vmhostId);
            taskService.updateById(task);
            // 添加导入操作系统任务
            Task importTask = new Task();
            long time = System.currentTimeMillis();
            importTask.setType(TaskType.IMPORT_SYSTEM_DISK);
            importTask.setVmid(vmId);
            importTask.setNodeid(vmParams.getNodeid());
            importTask.setHostid(vmhostId);
            importTask.setStatus(0);
            importTask.setCreateDate(time);
            HashMap<Object,Object> importParams = new HashMap<>();
            importParams.put("os", vmParams.getOs());
            importParams.put("size", vmParams.getSystemDiskSize());
            importTask.setParams(importParams);
            if (taskService.insertTask(importTask)) {
                // 存入成功，修改任务状态为2
                task.setStatus(2);
                taskService.updateById(task);
                // 获取数据库中的虚拟机信息
                Vmhost vmhost = vmhostService.getById(vmhostId);
                // 增加虚拟机task
                vmhost.getTask().put(String.valueOf(time), importTask.getId());
                vmhostService.updateById(vmhost);
            }else {
                // 存入失败，修改任务状态为3
                task.setStatus(3);
                // 记录错误信息
                task.setError("创建导入操作系统任务失败");
                taskService.updateById(task);
            }
        }
    }
}
