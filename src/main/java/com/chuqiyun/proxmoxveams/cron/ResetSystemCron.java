package com.chuqiyun.proxmoxveams.cron;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
import com.chuqiyun.proxmoxveams.constant.TaskType;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Os;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.OsService;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

import static com.chuqiyun.proxmoxveams.constant.TaskType.*;

/**
 * @author mryunqi
 * @date 2023/9/1
 */
@Component
@EnableScheduling
public class ResetSystemCron {
    @Resource
    private MasterService masterService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private TaskService taskService;
    @Resource
    private OsService osService;

    /**
     * 重装系统
     */
    @Async
    @Scheduled(fixedDelay = 1000)
    public void reinstallSystemCron() {
        QueryWrapper<Task> taskQueryWrapper = new QueryWrapper<>();
        taskQueryWrapper.eq("type", REINSTALL_VM);
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
        // 获取vm信息
        Vmhost vmhost = vmhostService.getById(task.getHostid());
        // 获取系统信息
        Map<Object, Object> systemMap = task.getParams();
        // 判断是否为空
        if (systemMap == null) {
            return;
        }
        UnifiedLogger.log(UnifiedLogger.LogType.TASK_RESET_SYSTEM, "执行重装系统任务: NodeID:{} VM-ID:{}", node.getId(), task.getVmid());

        String osName = systemMap.get("osName").toString();
        String newPassword;
        try {
            newPassword = systemMap.get("newPassword").toString();
        }catch (Exception e){
            newPassword = "";
        }

        boolean resetDataDisk = Boolean.parseBoolean(systemMap.get("resetDataDisk").toString());

        // 获取os别称
        Os os = osService.isExistOs(osName);
        vmhost.setOsName(os.getFileName());
        vmhost.setOsType(os.getOsType());
        vmhost.setOs(os.getName());
        // 修改vm信息
        vmhostService.updateById(vmhost);

        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();

        // 删除系统盘
        proxmoxApiUtil.deleteVmDisk(node,authentications,vmhost.getVmid(),"scsi0");

        // 删除数据盘
        if (resetDataDisk){
            Map<Object, Object> dataDiskDataMap = vmhost.getDataDisk();
            if (dataDiskDataMap != null){
                // 变量键，键为数据盘的索引
                for (Object key : dataDiskDataMap.keySet()){
                    // 删除数据盘
                    proxmoxApiUtil.deleteVmDisk(node,authentications,vmhost.getVmid(),"scsi"+key);
                }
            }
        }
        // 添加导入操作系统任务
        Task importTask = new Task();
        long time = System.currentTimeMillis();
        importTask.setType(TaskType.IMPORT_SYSTEM_DISK);
        importTask.setVmid(vmhost.getVmid());
        importTask.setNodeid(vmhost.getNodeid());
        importTask.setHostid(vmhost.getId());
        importTask.setStatus(0);
        importTask.setCreateDate(time);
        HashMap<Object,Object> importParams = new HashMap<>();
        importParams.put("os", osName);
        importParams.put("systemDiskSize", vmhost.getSystemDiskSize());
        importTask.setParams(importParams);
        if (!taskService.insertTask(importTask)) {
            UnifiedLogger.error(UnifiedLogger.LogType.TASK_CREATE_VM, "创建导入操作系统任务失败");
            // 存入失败，修改任务状态为3
            importTask.setStatus(3);
            // 记录错误信息
            importTask.setError("创建导入操作系统任务失败");
            taskService.updateById(importTask);
            return;
        }
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
                return;
            }
        } while (taskStatus.getStatus() != 2);
        // 任务状态为2，任务成功
        // 创建修改系统盘大小任务
        UnifiedLogger.log(UnifiedLogger.LogType.TASK_UPDATE_SYSTEM_DISK,"添加创建修改系统盘大小任务: NodeID:{} VM-ID:{}",node.getId(), task.getVmid());
        time = System.currentTimeMillis();
        Task updateSystemDiskTask = new Task();
        updateSystemDiskTask.setNodeid(vmhost.getNodeid());
        updateSystemDiskTask.setHostid(vmhost.getId());
        updateSystemDiskTask.setVmid(vmhost.getVmid());
        updateSystemDiskTask.setType(UPDATE_SYSTEM_DISK_SIZE);
        updateSystemDiskTask.setStatus(0);
        updateSystemDiskTask.setParams(importParams);
        updateSystemDiskTask.setCreateDate(time);
        if (!taskService.save(updateSystemDiskTask)){
            UnifiedLogger.warn(UnifiedLogger.LogType.TASK_UPDATE_SYSTEM_DISK,"添加创建修改系统盘大小任务: NodeID:{} VM-ID:{} 失败",node.getId(), task.getVmid());
            // 存入失败，修改任务状态为3
            updateSystemDiskTask.setStatus(3);
            // 记录错误信息
            updateSystemDiskTask.setError("创建导入操作系统任务失败");
            taskService.updateById(updateSystemDiskTask);
            return;
        }
        // 等待修改系统盘大小任务完成
        do {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // 获取任务
            taskStatus = taskService.getById(updateSystemDiskTask.getId());
            // 判断任务状态
            if (taskStatus.getStatus() == 3) {
                // 任务状态为3，任务失败
                // 结束任务
                return;
            }
        } while (taskStatus.getStatus() != 2);
        // 任务状态为2，任务成功
        // 判断是否需要重置数据盘
        if (!resetDataDisk){
            // 判断新密码是否为空
            if (!newPassword.equals("")){
                // 创建重置密码任务
                UnifiedLogger.log(
                        UnifiedLogger.LogType.TASK_RESET_PASSWORD,
                        "添加创建重置密码任务: NodeID:{} VM-ID:{}",node.getId(), task.getVmid());
                time = System.currentTimeMillis();
                Task resetPasswordTask = new Task();
                resetPasswordTask.setNodeid(vmhost.getNodeid());
                resetPasswordTask.setHostid(vmhost.getId());
                resetPasswordTask.setVmid(vmhost.getVmid());
                resetPasswordTask.setType(RESET_PASSWORD);
                resetPasswordTask.setStatus(0);
                HashMap<Object,Object> resetPasswordParams = new HashMap<>();
                resetPasswordParams.put("newPassword", newPassword);
                resetPasswordTask.setParams(resetPasswordParams);
                resetPasswordTask.setCreateDate(time);
                if (!taskService.save(resetPasswordTask)){
                    UnifiedLogger.warn(
                            UnifiedLogger.LogType.TASK_RESET_PASSWORD,
                            "添加创建重置密码任务: NodeID:{} VM-ID:{} 失败",node.getId(), task.getVmid());
                    // 存入失败，修改任务状态为3
                    resetPasswordTask.setStatus(3);
                    // 记录错误信息
                    resetPasswordTask.setError("创建重置密码任务失败");
                    taskService.updateById(resetPasswordTask);
                    return;
                }
                // 等待重置密码任务完成
                do {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    // 获取任务
                    taskStatus = taskService.getById(resetPasswordTask.getId());
                    // 判断任务状态
                    if (taskStatus.getStatus() == 3) {
                        // 任务状态为3，任务失败
                        // 结束任务
                        return;
                    }
                } while (taskStatus.getStatus() != 2);
            }

        }else {
            // 创建数据盘任务
            UnifiedLogger.log(UnifiedLogger.LogType.TASK_CREATE_DATA_DISK,"添加创建数据盘任务: NodeID:{} VM-ID:{}",node.getId(), task.getVmid());
            time = System.currentTimeMillis();
            Task createDataDiskTask = new Task();
            createDataDiskTask.setNodeid(vmhost.getNodeid());
            createDataDiskTask.setHostid(vmhost.getId());
            createDataDiskTask.setVmid(vmhost.getVmid());
            createDataDiskTask.setType(CREATE_DATA_DISK);
            createDataDiskTask.setStatus(0);
            createDataDiskTask.setParams(vmhost.getDataDisk());
            createDataDiskTask.setCreateDate(time);
            if (!taskService.save(createDataDiskTask)){
                UnifiedLogger.error(UnifiedLogger.LogType.TASK_CREATE_DATA_DISK,"添加创建数据盘任务: NodeID:{} VM-ID:{} 失败",node.getId(), task.getVmid());

                // 修改任务状态为失败
                createDataDiskTask.setStatus(3);
                createDataDiskTask.setError("创建数据盘任务失败");
                taskService.updateById(createDataDiskTask);
                return;
            }
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
            // 任务状态为2，任务成功
            // 判断新密码是否为空
            if (!newPassword.equals("")){
                // 创建重置密码任务
                UnifiedLogger.log(
                        UnifiedLogger.LogType.TASK_RESET_PASSWORD,
                        "添加创建重置密码任务: NodeID:{} VM-ID:{}",node.getId(), task.getVmid());
                time = System.currentTimeMillis();
                Task resetPasswordTask = new Task();
                resetPasswordTask.setNodeid(vmhost.getNodeid());
                resetPasswordTask.setHostid(vmhost.getId());
                resetPasswordTask.setVmid(vmhost.getVmid());
                resetPasswordTask.setType(RESET_PASSWORD);
                resetPasswordTask.setStatus(0);
                HashMap<Object,Object> resetPasswordParams = new HashMap<>();
                resetPasswordParams.put("newPassword", newPassword);
                resetPasswordTask.setParams(resetPasswordParams);
                resetPasswordTask.setCreateDate(time);
                if (!taskService.save(resetPasswordTask)){
                    UnifiedLogger.warn(UnifiedLogger.LogType.TASK_RESET_PASSWORD, "添加创建重置密码任务: NodeID:{} VM-ID:{} 失败",node.getId(), task.getVmid());
                    // 存入失败，修改任务状态为3
                    resetPasswordTask.setStatus(3);
                    // 记录错误信息
                    resetPasswordTask.setError("创建重置密码任务失败");
                    taskService.updateById(resetPasswordTask);
                    return;
                }
                // 等待重置密码任务完成
                do {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    // 获取任务
                    taskStatus = taskService.getById(resetPasswordTask.getId());
                    // 判断任务状态
                    if (taskStatus.getStatus() == 3) {
                        // 任务状态为3，任务失败
                        // 结束任务
                        return;
                    }
                } while (taskStatus.getStatus() != 2);
            }
        }
        // 任务状态为2，任务成功
        // 创建修改启动项任务
        UnifiedLogger.log(UnifiedLogger.LogType.TASK_UPDATE_BOOT,"添加创建修改启动项任务: NodeID:{} VM-ID:{}",node.getId(), task.getVmid());

        time = System.currentTimeMillis();
        Task updateBootDiskTask = new Task();
        updateBootDiskTask.setNodeid(vmhost.getNodeid());
        updateBootDiskTask.setHostid(vmhost.getId());
        updateBootDiskTask.setVmid(vmhost.getVmid());
        updateBootDiskTask.setType(UPDATE_VM_BOOT);
        updateBootDiskTask.setStatus(0);
        Map<Object,Object> bootDiskMap = new HashMap<>();
        bootDiskMap.put("boot","order=scsi0;ide2;net0");
        updateBootDiskTask.setParams(bootDiskMap);
        updateBootDiskTask.setCreateDate(time);
        if (!taskService.insertTask(updateBootDiskTask)){
            UnifiedLogger.error(UnifiedLogger.LogType.TASK_UPDATE_BOOT,"添加创建修改启动项任务: NodeID:{} VM-ID:{} 失败",node.getId(), task.getVmid());
            // 修改任务状态为失败
            updateBootDiskTask.setStatus(3);
            updateBootDiskTask.setError("创建修改启动项任务失败");
            taskService.updateById(updateBootDiskTask);
        }
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
                return;
            }
        } while (taskStatus.getStatus() != 2);
        // 任务状态为2，任务成功
        // 创建开机任务
        Task startTask = new Task();
        time = System.currentTimeMillis();
        startTask.setNodeid(vmhost.getNodeid());
        startTask.setVmid(vmhost.getVmid());
        startTask.setHostid(vmhost.getId());
        startTask.setType(START_VM);
        startTask.setStatus(0);
        startTask.setCreateDate(time);
        if (!taskService.insertTask(startTask)){
            UnifiedLogger.error(UnifiedLogger.LogType.TASK_START_VM,"添加创建开机任务: NodeID:{} VM-ID:{} 失败",node.getId(), task.getVmid());
            // 修改任务状态为失败
            startTask.setStatus(3);
            startTask.setError("创建修改启动项任务失败");
            taskService.updateById(startTask);
        }

        // 更新主线程任务task状态为2
        task.setStatus(2);
        taskService.updateById(task);
    }
}
