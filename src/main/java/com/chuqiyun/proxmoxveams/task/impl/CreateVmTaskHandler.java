package com.chuqiyun.proxmoxveams.task.impl;

import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
import com.chuqiyun.proxmoxveams.constant.TaskStatus;
import com.chuqiyun.proxmoxveams.constant.TaskType;
import com.chuqiyun.proxmoxveams.dto.VmParams;
import com.chuqiyun.proxmoxveams.entity.Config;
import com.chuqiyun.proxmoxveams.entity.Ippool;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.service.*;
import com.chuqiyun.proxmoxveams.task.TaskHandler;
import com.chuqiyun.proxmoxveams.utils.EntityHashMapConverterUtil;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class CreateVmTaskHandler implements TaskHandler {

    @Resource
    private TaskService taskService;

    @Resource
    private VmhostService vmhostService;

    @Resource
    private CreateVmService createVmService;

    @Resource
    private IppoolService ippoolService;

    @Resource
    private MasterService masterService;

    @Resource
    private ConfigService configService;


    @Override
    public void process(Task task) {
        // 设置任务状态为1 (正在执行)
        task.setStatus(TaskStatus.RUNNING);
        taskService.updateById(task);

        // 获取任务的参数
        Map<Object, Object> params = task.getParams();
        VmParams vmParams;
        try {
            vmParams = EntityHashMapConverterUtil.convertToEntity(params, VmParams.class);
        } catch (IllegalAccessException | InstantiationException e) {
            UnifiedLogger.error(UnifiedLogger.LogType.TASK_CREATE_VM, "创建虚拟机任务参数转换失败");
            task.setStatus(TaskStatus.FAILURE);
            task.setError("创建虚拟机任务参数转换失败");
            taskService.updateById(task);
            return;
        }

        // 创建虚拟机
        int vmIdInit = vmhostService.getNewVmid(vmParams.getNodeid());
        Integer vmhostId = vmhostService.addVmhost(vmIdInit, vmParams);

        if (vmhostId == null) {
            UnifiedLogger.error(UnifiedLogger.LogType.TASK_CREATE_VM, "创建虚拟机信息存入数据库失败");

            task.setStatus(TaskStatus.FAILURE);
            task.setError("创建虚拟机信息存入数据库失败");
            taskService.updateById(task);
            return;
        }
        UnifiedLogger.log(UnifiedLogger.LogType.TASK_CREATE_VM, "创建虚拟机信息存入数据库成功");
        task.setStatus(TaskStatus.API_NON_ASYNC_SUCCESS);
        task.setVmid(vmIdInit);
        task.setHostid(vmhostId);
        taskService.updateById(task);

        // 分配IP
        List<String> ipList = vmParams.getIpList();
        for (String ip : ipList) {
            Ippool ippool = ippoolService.getIppoolByIp(ip);
            if (ippool != null) {
                ippool.setVmId(vmIdInit);
                ippool.setStatus(1);
                ippoolService.updateById(ippool);
            }
        }

        int vmId = createVmService.createPveVm(vmParams, vmIdInit);
        if (vmId == 0) {
            UnifiedLogger.error(UnifiedLogger.LogType.TASK_CREATE_VM, "创建基础虚拟机失败");
            task.setStatus(TaskStatus.FAILURE);
            task.setError("创建基础虚拟机失败");
            taskService.updateById(task);
            return;
        }

        UnifiedLogger.log(UnifiedLogger.LogType.TASK_CREATE_VM, "创建基础虚拟机成功");

        // 获取node信息
        Master node = masterService.getById(vmParams.getNodeid());
        //初始添加Nat规则
        if (vmParams.getIfnat() == 1){
            int dest_port;
            if (Objects.equals(vmParams.getOsType(), "windows")) {
                dest_port = 3389;
            } else {
                dest_port = 22;
            }
            String dest_ip = ipList.get(0);
            int s_port = ThreadLocalRandom.current().nextInt(1000, 65536);
            if (!vmhostService.addVmhostNat(node.getHost(),s_port, dest_ip, dest_port, "tcp", vmhostId)){
                s_port = ThreadLocalRandom.current().nextInt(1000, 65536);
                vmhostService.addVmhostNat(node.getHost(),s_port, dest_ip, dest_port, "tcp", vmhostId);
            }
            System.out.println("[NAT] 创建默认远程NAT: 虚拟机ID:" + vmhostId + " VM-ID:" + task.getHostid() + " 完成");
        }
        task.setStatus(TaskStatus.SUCCESS);
        // 设置主线任务type
        task.setMasterType(TaskType.CREATE_VM);
        taskService.updateById(task);

        // 添加后续任务
        addSubTasks(params, vmIdInit, vmParams.getNodeid(), vmhostId, task.getId());
    }

    private void addSubTasks(Map<Object, Object> params, Integer vmIdInit,Integer nodeid, Integer vmhostId, Integer masterId) {
        // 添加导入操作系统任务
        Task importTask = new Task();
        importTask.setType(TaskType.IMPORT_SYSTEM_DISK);
        importTask.setVmid(vmIdInit);
        importTask.setNodeid(nodeid);
        importTask.setHostid(vmhostId);
        importTask.setMasterId(masterId);
        importTask.setStatus(TaskStatus.WAITING);
        importTask.setCreateDate(System.currentTimeMillis());
        importTask.setParams(params);
        taskService.insertTask(importTask);

        // 添加虚拟机任务流程
        vmhostService.addVmHostTask(vmhostId, importTask.getId());
    }

    @Override
    public int getType() {
        return TaskType.CREATE_VM;
    }
}
