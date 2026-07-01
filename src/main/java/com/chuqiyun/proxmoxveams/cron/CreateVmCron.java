package com.chuqiyun.proxmoxveams.cron;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.common.TimedLock;
import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.common.VmCreateLock;
import com.chuqiyun.proxmoxveams.constant.TaskType;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.entity.Ippool;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Subnetpool;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.dto.VmParams;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.*;
import com.chuqiyun.proxmoxveams.utils.EntityHashMapConverterUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

import static com.chuqiyun.proxmoxveams.constant.TaskType.*;

/**
 * @author mryunqi
 * @date 2023/6/30
 */
@Slf4j
@Component
@EnableScheduling
public class CreateVmCron {
    private static final String DEFAULT_CREATE_VM_STORAGE = "local-lvm";
    private static final String NETWORK_TYPE_VPC = "vpc";

    @Resource
    private VmhostService vmhostService;
    @Resource
    private TaskService taskService;
    @Resource
    private CreateVmService createVmService;
    @Resource
    private IppoolService ippoolService;
    @Resource
    private SubnetpoolService subnetpoolService;
    @Resource
    private MasterService masterService;

    /**
     * 创建虚拟机
     */
    @Async
    @Scheduled(fixedDelay = 2000)
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
                UnifiedLogger.error(UnifiedLogger.LogType.TASK_CREATE_VM, "创建虚拟机任务参数转换失败");
                // 更新任务状态为3 3为失败
                task.setStatus(3);
                // 记录错误信息
                task.setError("创建虚拟机任务参数转换失败");
                taskService.updateById(task);
                e.printStackTrace();
                // 结束任务
                return;
            }
            String hostname = vmParams.getHostname();
            TimedLock timedLock = VmCreateLock.getTimedLock(hostname);
            ReentrantLock lock = timedLock.getLock();

            if (!lock.tryLock()) {
                UnifiedLogger.error(UnifiedLogger.LogType.TASK_CREATE_VM, "【重复创建提示】已被锁拦截，主机名："  + vmParams.getHostname());
                // 更新任务状态为3 3为失败
                task.setStatus(3);
                // 记录错误信息
                task.setError("【重复创建提示】已被锁拦截，主机名：" + vmParams.getHostname());
                taskService.updateById(task);
                return;
            }
            Map<Object, Object> taskMap = new HashMap<>();
            taskMap.put(String.valueOf(System.currentTimeMillis()), task.getId());
            //vmParams.setTask(params);
            int vmIdInit = vmhostService.getNewVmid(vmParams.getNodeid());
            Master createNode = masterService.getById(vmParams.getNodeid());
            if (createNode == null) {
                finishCreateVmFailed(task, null, vmParams, vmIdInit, "创建基础虚拟机失败，节点不存在");
                return;
            }
            vmParams.setStorage(getCreateVmStorage(vmParams.getStorage(), createNode));

            // 将创建的虚拟机信息存入数据库
            Integer vmhostId = vmhostService.addVmhost(vmIdInit, vmParams);
            Vmhost vmhost;
            // 判断是否存入成功
            if (vmhostId == null) {
                UnifiedLogger.error(UnifiedLogger.LogType.TASK_CREATE_VM, "创建虚拟机信息存入数据库失败");
                // 存入失败，修改任务状态为3
                task.setStatus(3);
                // 记录错误信息
                task.setError("创建虚拟机信息存入数据库失败");
                taskService.updateById(task);
                // 结束任务
                return;
            }else {
                UnifiedLogger.log(UnifiedLogger.LogType.TASK_CREATE_VM, "创建虚拟机信息存入数据库成功");
                // 设置为4 4为提示api接口调用时非异步成功
                vmParams.setHostid(vmhostId);
                task.setStatus(4);
                task.setVmid(vmIdInit);
                task.setHostid(vmhostId);
                taskService.updateById(task);
            }

            List<String> ipList = vmParams.getIpList();
            for (String ip : ipList){
                if (isVpcNetwork(vmParams)) {
                    Subnetpool subnetpool = getSubnetpoolByIpAndNodeId(ip, vmParams.getNodeid(), vmParams.getVpcSubnetId());
                    if (subnetpool != null){
                        subnetpool.setVmId(vmIdInit);
                        subnetpool.setStatus(1);
                        subnetpoolService.updateById(subnetpool);
                    }
                } else {
                    // 根据ip查询ip实体类
                    Ippool ippool = getIppoolByIpAndNodeId(ip, vmParams.getNodeid());
                    if (ippool != null){
                        ippool.setVmId(vmIdInit);
                        ippool.setStatus(1);
                        ippoolService.updateById(ippool);
                    }
                }
            }

            if (isVpcNetwork(vmParams)) {
                bindVpcPublicIps(vmParams, vmIdInit);
            }

            int vmId;
            try {
                vmId = createVmService.createPveVm(vmParams, vmIdInit);
            } catch (Exception e) {
                UnifiedLogger.error(UnifiedLogger.LogType.TASK_CREATE_VM, "创建基础虚拟机异常: NodeID:{} VM-ID:{}", vmParams.getNodeid(), vmIdInit);
                finishCreateVmFailed(task, vmhostId, vmParams, vmIdInit, "创建基础虚拟机失败: " + getExceptionMessage(e));
                e.printStackTrace();
                return;
            }
            // 判断是否创建成功
            if (vmId == 0) {
                UnifiedLogger.error(UnifiedLogger.LogType.TASK_CREATE_VM, "创建基础虚拟机失败");
                finishCreateVmFailed(task, vmhostId, vmParams, vmIdInit, "创建基础虚拟机失败");
                // 结束任务
                return;
            }

            if (isVpcNetwork(vmParams) && !addVpcIpForwards(vmhostId, vmParams)) {
                UnifiedLogger.error(UnifiedLogger.LogType.TASK_CREATE_VM, "VPC公网IP转发绑定失败 NodeID:{} VM-ID:{}", vmParams.getNodeid(), vmIdInit);
                finishCreateVmFailed(task, vmhostId, vmParams, vmIdInit, "VPC公网IP转发绑定失败");
                return;
            }

            UnifiedLogger.log(UnifiedLogger.LogType.TASK_CREATE_VM, "创建基础虚拟机成功");
            /*String createTime = String.valueOf(System.currentTimeMillis());
            HashMap<Object, Object> taskMap = new HashMap<>();
            taskMap.put(createTime, task.getId());
            vmParams.setTask(taskMap);*/
            //初始添加Nat规则
            if (vmParams.getIfnat() == 1){
                int dest_port;
                if (Objects.equals(vmParams.getOsType(), "windows")) {
                    dest_port = 3389;
                } else {
                    dest_port = 22;
                }
                String dest_ip = ipList.get(0);
                Master node = masterService.getById(vmParams.getNodeid());
                int s_port = ThreadLocalRandom.current().nextInt(1000, 65536);
                if (!vmhostService.addVmhostNat(node.getHost(), s_port, dest_ip, dest_port, "tcp", vmhostId)){
                    s_port = ThreadLocalRandom.current().nextInt(1000, 65536);
                    vmhostService.addVmhostNat(node.getHost(), s_port, dest_ip, dest_port, "tcp", vmhostId);
                }
                System.out.println("[NAT] 创建默认远程NAT: 虚拟机ID:" + vmhostId + " VM-ID:" + task.getHostid() + " 完成");
            }
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
            if (!taskService.insertTask(importTask)) {
                UnifiedLogger.error(UnifiedLogger.LogType.TASK_CREATE_VM, "创建导入操作系统任务失败");
                // 存入失败，修改任务状态为3
                task.setStatus(3);
                // 记录错误信息
                vmhost = vmhostService.getById(vmhostId);
                vmhost.setStatus(1);
                vmhostService.updateById(vmhost);
                task.setError("创建导入操作系统任务失败");
                taskService.updateById(importTask);
                return;
            }
            // 添加任务流程
            vmhostService.addVmHostTask(vmhostId, importTask.getId());
            Task taskStatus;
            // 等待导入操作系统任务完成
            do {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // 获取任务
                taskStatus = taskService.getById(importTask.getId());
                // 判断任务状态
                if (taskStatus.getStatus() == 3) {
                    // 任务状态为3，任务失败
                    // 结束任务
                    vmhost = vmhostService.getById(vmhostId);
                    vmhost.setStatus(1);
                    vmhostService.updateById(vmhost);
                    return;
                }
            } while (taskStatus.getStatus() != 2);
            // 任务状态为2，任务成功

            // 修改系统盘IO限制
            UnifiedLogger.log(UnifiedLogger.LogType.UpdateSystemDiskIOLimit, "修改系统盘IO限制");
            time = System.currentTimeMillis();
            Task updateSystemDiskIOLimitTask = new Task();
            updateSystemDiskIOLimitTask.setNodeid(vmParams.getNodeid());
            updateSystemDiskIOLimitTask.setHostid(vmhostId);
            updateSystemDiskIOLimitTask.setVmid(vmId);
            updateSystemDiskIOLimitTask.setType(UPDATE_IO_SYSTEM_DISK);
            updateSystemDiskIOLimitTask.setStatus(0);
            updateSystemDiskIOLimitTask.setParams(task.getParams());
            updateSystemDiskIOLimitTask.setCreateDate(time);
            if (!taskService.save(updateSystemDiskIOLimitTask)){
                UnifiedLogger.warn(UnifiedLogger.LogType.UpdateSystemDiskIOLimit,"添加修改系统盘IO限制任务失败: NodeID:{} VM-ID:{}",vmParams.getNodeid(),vmId);
                // 存入失败，修改任务状态为3
                task.setStatus(3);
                // 记录错误信息
                task.setError("添加修改系统盘IO限制任务失败");
                vmhost = vmhostService.getById(vmhostId);
                vmhost.setStatus(1);
                vmhostService.updateById(vmhost);
                return;
            }
            // 添加任务流程
            vmhostService.addVmHostTask(vmhostId, updateSystemDiskIOLimitTask.getId());
            // 等待修改系统盘IO限制任务完成
            do {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // 获取任务
                taskStatus = taskService.getById(updateSystemDiskIOLimitTask.getId());
                // 判断任务状态
                if (taskStatus.getStatus() == 3) {
                    // 任务状态为3，任务失败
                    // 结束任务
                    return;
                }
            } while (taskStatus.getStatus() != 2);
            // 任务状态为2，任务成功
            // 创建修改系统盘大小任务
            UnifiedLogger.log(UnifiedLogger.LogType.TASK_UPDATE_SYSTEM_DISK,"添加创建修改系统盘大小任务: NodeID:{} VM-ID:{}",vmParams.getNodeid(),vmId);
            time = System.currentTimeMillis();
            Task updateSystemDiskTask = new Task();
            updateSystemDiskTask.setNodeid(vmParams.getNodeid());
            updateSystemDiskTask.setHostid(vmhostId);
            updateSystemDiskTask.setVmid(vmId);
            updateSystemDiskTask.setType(UPDATE_SYSTEM_DISK_SIZE);
            updateSystemDiskTask.setStatus(0);
            updateSystemDiskTask.setParams(task.getParams());
            updateSystemDiskTask.setCreateDate(time);
            if (!taskService.save(updateSystemDiskTask)){
                UnifiedLogger.warn(UnifiedLogger.LogType.TASK_UPDATE_SYSTEM_DISK,"添加创建修改系统盘大小任务: NodeID:{} VM-ID:{} 失败",vmParams.getNodeid(),vmId);
                // 存入失败，修改任务状态为3
                task.setStatus(3);
                // 记录错误信息
                vmhost = vmhostService.getById(vmhostId);
                vmhost.setStatus(1);
                vmhostService.updateById(vmhost);
                task.setError("创建修改系统盘大小任务");
                taskService.updateById(updateSystemDiskTask);
                return;
            }
            // 添加任务流程
            vmhostService.addVmHostTask(vmhostId, updateSystemDiskTask.getId());


            // 等待修改系统盘大小任务完成
            do {
                try {
                    Thread.sleep(20000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // 获取任务
                taskStatus = taskService.getById(updateSystemDiskTask.getId());
                // 判断任务状态
                if (taskStatus.getStatus() == 3) {
                    // 任务状态为3，任务失败
                    // 结束任务
                    vmhost = vmhostService.getById(vmhostId);
                    vmhost.setStatus(1);
                    vmhostService.updateById(vmhost);
                    return;
                }
            } while (taskStatus.getStatus() != 2);
            // 任务状态为2，任务成功
            // 创建数据盘任务
            // 判断是否有数据盘或者数据盘大小是否为0
            if (vmParams.getDataDisk() != null && vmParams.getDataDisk().size() > 0){
                UnifiedLogger.log(UnifiedLogger.LogType.TASK_CREATE_DATA_DISK,"添加创建数据盘任务: NodeID:{} VM-ID:{}",vmParams.getNodeid(),vmId);
                time = System.currentTimeMillis();
                Task createDataDiskTask = new Task();
                createDataDiskTask.setNodeid(vmParams.getNodeid());
                createDataDiskTask.setHostid(vmhostId);
                createDataDiskTask.setVmid(vmId);
                createDataDiskTask.setType(CREATE_DATA_DISK);
                createDataDiskTask.setStatus(0);
                createDataDiskTask.setParams(vmParams.getDataDisk());
                createDataDiskTask.setCreateDate(time);
                if (!taskService.save(createDataDiskTask)){
                    UnifiedLogger.error(UnifiedLogger.LogType.TASK_CREATE_DATA_DISK,"添加创建数据盘任务: NodeID:{} VM-ID:{} 失败",vmParams.getNodeid(),vmId);
                    vmhost = vmhostService.getById(vmhostId);
                    vmhost.setStatus(1);
                    vmhostService.updateById(vmhost);
                    // 修改任务状态为失败
                    task.setStatus(3);
                    task.setError("创建数据盘任务失败");
                    taskService.updateById(createDataDiskTask);
                    return;
                }
                // 添加任务流程
                vmhostService.addVmHostTask(vmhostId,createDataDiskTask.getId());

                // 等待创建数据盘任务完成
                do {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    // 获取任务
                    taskStatus = taskService.getById(createDataDiskTask.getId());
                    // 判断任务状态
                    if (taskStatus.getStatus() == 3) {
                        // 任务状态为3，任务失败
                        // 结束任务
                        return;
                    }
                } while (taskStatus.getStatus() != 2);
            }
            // 任务状态为2，任务成功
            // 创建修改启动项任务
            UnifiedLogger.log(UnifiedLogger.LogType.TASK_UPDATE_BOOT,"添加创建修改启动项任务: NodeID:{} VM-ID:{}",vmParams.getNodeid(),vmId);

            time = System.currentTimeMillis();
            Task updateBootDiskTask = new Task();
            updateBootDiskTask.setNodeid(vmParams.getNodeid());
            updateBootDiskTask.setHostid(vmhostId);
            updateBootDiskTask.setVmid(vmId);
            updateBootDiskTask.setType(UPDATE_VM_BOOT);
            updateBootDiskTask.setStatus(0);
            Map<Object,Object> bootDiskMap = new HashMap<>();
            bootDiskMap.put("boot","order=scsi0;ide2;net0");
            updateBootDiskTask.setParams(bootDiskMap);
            updateBootDiskTask.setCreateDate(time);
            if (!taskService.insertTask(updateBootDiskTask)){
                UnifiedLogger.error(UnifiedLogger.LogType.TASK_UPDATE_BOOT,"添加创建修改启动项任务: NodeID:{} VM-ID:{} 失败",vmParams.getNodeid(),vmId);
                // 修改任务状态为失败
                task.setStatus(3);
                task.setError("创建修改启动项任务失败");
                taskService.updateById(updateBootDiskTask);
                vmhost = vmhostService.getById(vmhostId);
                vmhost.setStatus(1);
                vmhostService.updateById(vmhost);
            }
            // 添加任务流程
            vmhostService.addVmHostTask(vmhostId,updateBootDiskTask.getId());

            // 等待创建修改启动项任务完成
            do {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // 获取任务
                taskStatus = taskService.getById(updateBootDiskTask.getId());
                // 判断任务状态
                if (taskStatus.getStatus() == 3) {
                    // 任务状态为3，任务失败
                    // 结束任务
                    vmhost = vmhostService.getById(vmhostId);
                    vmhost.setStatus(1);
                    vmhostService.updateById(vmhost);
                    return;
                }
            } while (taskStatus.getStatus() != 2);

            // 任务状态为2，任务成功
            // 创建开机任务
            Task startTask = new Task();
            time = System.currentTimeMillis();
            startTask.setNodeid(vmParams.getNodeid());
            startTask.setVmid(vmId);
            startTask.setHostid(vmhostId);
            startTask.setType(START_VM);
            startTask.setStatus(0);
            startTask.setCreateDate(time);
            if (!taskService.insertTask(startTask)){
                UnifiedLogger.error(UnifiedLogger.LogType.TASK_START_VM,"添加创建开机任务: NodeID:{} VM-ID:{} 失败",vmParams.getNodeid(),vmId);
                // 修改任务状态为失败
                task.setStatus(3);
                task.setError("添加创建开机任务失败");
                taskService.updateById(startTask);
                vmhost = vmhostService.getById(vmhostId);
                vmhost.setStatus(1);
                vmhostService.updateById(vmhost);
            }
            // 添加任务流程
            vmhost = vmhostService.getById(vmhostId);
            vmhost.setStatus(0);
            vmhostService.updateById(vmhost);
            vmhostService.addVmHostTask(vmhostId, startTask.getId());
            // 更新主线程任务task状态为2
            task.setStatus(2);
            taskService.updateById(task);

        }

    }

    /**
     * @Author: 星禾
     * @Description: 根据节点和IP精确查询IP池记录
     * @DateTime: 2026/6/6 12:40
     */
    private Ippool getIppoolByIpAndNodeId(String ip, Integer nodeId) {
        if (ip == null || ip.trim().isEmpty() || nodeId == null) {
            return null;
        }
        QueryWrapper<Ippool> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("ip", ip.trim());
        queryWrapper.eq("node_id", nodeId);
        queryWrapper.last("limit 1");
        return ippoolService.getOne(queryWrapper);
    }

    private Subnetpool getSubnetpoolByIpAndNodeId(String ip, Integer nodeId, Integer subnetId) {
        if (ip == null || ip.trim().isEmpty() || nodeId == null || subnetId == null) {
            return null;
        }
        QueryWrapper<Subnetpool> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("ip", ip.trim());
        queryWrapper.eq("node_id", nodeId);
        queryWrapper.eq("subnat_id", subnetId);
        queryWrapper.last("limit 1");
        return subnetpoolService.getOne(queryWrapper);
    }

    private void bindVpcPublicIps(VmParams vmParams, Integer vmId) {
        if (vmParams == null || vmId == null || vmParams.getPublicIpList() == null || vmParams.getPublicIpList().isEmpty()) {
            return;
        }
        for (String ip : vmParams.getPublicIpList()) {
            Ippool ippool = getIppoolByIpAndNodeId(ip, vmParams.getNodeid());
            if (ippool != null) {
                ippool.setVmId(vmId);
                ippool.setStatus(1);
                ippoolService.updateById(ippool);
            }
        }
    }

    private boolean addVpcIpForwards(Integer vmhostId, VmParams vmParams) {
        if (vmhostId == null || vmParams == null || vmParams.getIpList() == null || vmParams.getPublicIpList() == null) {
            return false;
        }
        if (vmParams.getIpList().isEmpty() || vmParams.getPublicIpList().size() < vmParams.getIpList().size()) {
            return false;
        }
        for (int i = 0; i < vmParams.getIpList().size(); i++) {
            String privateIp = vmParams.getIpList().get(i);
            String publicIp = vmParams.getPublicIpList().get(i);
            if (!vmhostService.addVmhostVpcIpForward(vmhostId, publicIp, privateIp)) {
                rollbackVpcIpForwards(vmhostId, vmParams, i);
                return false;
            }
        }
        return true;
    }

    private void rollbackVpcIpForwards(Integer vmhostId, VmParams vmParams, int createdCount) {
        if (vmhostId == null || vmParams == null || vmParams.getIpList() == null || vmParams.getPublicIpList() == null) {
            return;
        }
        int max = Math.min(createdCount, Math.min(vmParams.getIpList().size(), vmParams.getPublicIpList().size()));
        for (int i = 0; i < max; i++) {
            vmhostService.delVmhostVpcIpForward(vmhostId, vmParams.getPublicIpList().get(i), vmParams.getIpList().get(i));
        }
    }

    private boolean isVpcNetwork(VmParams vmParams) {
        return vmParams != null && NETWORK_TYPE_VPC.equalsIgnoreCase(vmParams.getNetworkType());
    }

    /**
     * @Author: 星禾
     * @Description: 标记创建虚拟机失败并回滚本次占用的IP
     * @DateTime: 2026/6/6 20:24
     */
    private void finishCreateVmFailed(Task task, Integer vmhostId, VmParams vmParams, Integer vmId, String error) {
        task.setStatus(3);
        task.setError(error);
        taskService.updateById(task);

        Vmhost vmhost = vmhostId == null ? null : vmhostService.getById(vmhostId);
        if (vmhost != null) {
            vmhost.setStatus(1);
            vmhostService.updateById(vmhost);
        }
        if (isVpcNetwork(vmParams)) {
            releaseCreatedVpcIps(vmParams, vmId);
            return;
        }
        releaseCreatedIps(vmParams, vmId);
    }

    private void releaseCreatedVpcIps(VmParams vmParams, Integer vmId) {
        if (vmParams == null || vmId == null || vmParams.getIpList() == null || vmParams.getIpList().isEmpty()) {
            return;
        }
        int releaseCount = 0;
        rollbackVpcIpForwards(vmParams.getHostid(), vmParams, vmParams.getPublicIpList() == null ? 0 : vmParams.getPublicIpList().size());
        for (String ip : vmParams.getIpList()) {
            Subnetpool subnetpool = getSubnetpoolByIpAndNodeId(ip, vmParams.getNodeid(), vmParams.getVpcSubnetId());
            if (subnetpool == null || !Objects.equals(subnetpool.getVmId(), vmId)) {
                continue;
            }
            subnetpool.setStatus(0);
            subnetpool.setVmId(0);
            if (subnetpoolService.updateById(subnetpool)) {
                releaseCount++;
            }
        }
        if (releaseCount > 0) {
            UnifiedLogger.log(UnifiedLogger.LogType.TASK_CREATE_VM, "创建基础虚拟机失败，已释放本次占用VPC IP数量: NodeID:{} VM-ID:{} Count:{}", vmParams.getNodeid(), vmId, releaseCount);
        }
        releaseCreatedPublicIps(vmParams, vmId);
    }

    private void releaseCreatedPublicIps(VmParams vmParams, Integer vmId) {
        if (vmParams == null || vmId == null || vmParams.getPublicIpList() == null || vmParams.getPublicIpList().isEmpty()) {
            return;
        }
        int releaseCount = 0;
        for (String ip : vmParams.getPublicIpList()) {
            Ippool ippool = getIppoolByIpAndNodeId(ip, vmParams.getNodeid());
            if (ippool == null || !Objects.equals(ippool.getVmId(), vmId)) {
                continue;
            }
            ippool.setStatus(0);
            ippool.setVmId(0);
            if (ippoolService.updateById(ippool)) {
                releaseCount++;
            }
        }
        if (releaseCount > 0) {
            UnifiedLogger.log(UnifiedLogger.LogType.TASK_CREATE_VM, "创建基础虚拟机失败，已释放本次占用VPC公网IP数量: NodeID:{} VM-ID:{} Count:{}", vmParams.getNodeid(), vmId, releaseCount);
        }
    }

    private void releaseCreatedIps(VmParams vmParams, Integer vmId) {
        if (vmParams == null || vmId == null || vmParams.getIpList() == null || vmParams.getIpList().isEmpty()) {
            return;
        }
        int releaseCount = 0;
        for (String ip : vmParams.getIpList()) {
            Ippool ippool = getIppoolByIpAndNodeId(ip, vmParams.getNodeid());
            if (ippool == null || !Objects.equals(ippool.getVmId(), vmId)) {
                continue;
            }
            ippool.setStatus(0);
            ippool.setVmId(0);
            if (ippoolService.updateById(ippool)) {
                releaseCount++;
            }
        }
        if (releaseCount > 0) {
            UnifiedLogger.log(UnifiedLogger.LogType.TASK_CREATE_VM, "创建基础虚拟机失败，已释放本次占用IP数量: NodeID:{} VM-ID:{} Count:{}", vmParams.getNodeid(), vmId, releaseCount);
        }
    }

    private String getExceptionMessage(Exception e) {
        if (e == null || e.getMessage() == null || e.getMessage().trim().isEmpty()) {
            return "未知错误";
        }
        return e.getMessage();
    }

    private String getCreateVmStorage(String storage, Master node) {
        String normalizedStorage = normalizeStorageText(storage);
        if (!isAutoStorage(normalizedStorage)) {
            return normalizedStorage;
        }
        String autoStorage = node == null ? null : normalizeStorageText(node.getAutoStorage());
        if (!isAutoStorage(autoStorage)) {
            return autoStorage;
        }
        return DEFAULT_CREATE_VM_STORAGE;
    }

    private boolean isAutoStorage(String storage) {
        return storage == null || storage.isEmpty() || "auto".equalsIgnoreCase(storage);
    }

    private String normalizeStorageText(String storage) {
        return storage == null ? null : storage.trim();
    }
}
