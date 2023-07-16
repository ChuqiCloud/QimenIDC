package com.chuqiyun.proxmoxveams.cron;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.*;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import com.chuqiyun.proxmoxveams.utils.SshUtil;
import com.jcraft.jsch.JSchException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

import static com.chuqiyun.proxmoxveams.constant.TaskType.*;

/**
 * @author mryunqi
 * @date 2023/6/21
 */
@Slf4j
@Component
@EnableScheduling
public class DiskCron {
    @Resource
    private MasterService masterService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private TaskService taskService;
    @Async
    @Scheduled(fixedDelay = 1000*60*10)  //每隔10分钟执行一次
    public void autoDiskName() {
        // 休眠10秒
        try {
            Thread.sleep(1000*10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        QueryWrapper<Master> queryWrap = new QueryWrapper<>();
        queryWrap.eq("status",0);
        // 获取所有
        List<Master> nodeList = masterService.list(queryWrap);
        for (Master node : nodeList){
            ArrayList<JSONObject> diskList = masterService.getDiskList(node.getId());
            Optional<String> maxStorage = diskList.stream()
                    .max(Comparator.comparingLong(disk -> disk.getLong("avail")))
                    .map(disk -> disk.getString("storage"));
            String maxStorageName = maxStorage.orElse(null);
            node.setAutoStorage(maxStorageName);
            masterService.updateById(node);
        }

    }

    @Async
    @Scheduled(fixedDelay = 1000)
    public void importDiskCron(){
        QueryWrapper<Task> taskQueryWrapper = new QueryWrapper<>();
        taskQueryWrapper.eq("type",IMPORT_SYSTEM_DISK);
        taskQueryWrapper.eq("status",1);
        // 获取正在进行的导入系统盘任务
        Page<Task> taskPageNow = taskService.getTaskList(1,2,taskQueryWrapper);
        // 判断正在进行的任务是否=2
        if (taskPageNow.getRecords().size() == 2){
            return;
        }
        taskQueryWrapper.clear();
        taskQueryWrapper.eq("type",IMPORT_SYSTEM_DISK);
        taskQueryWrapper.eq("status",0);
        // 获取未进行的导入系统盘任务
        Page<Task> taskPage = taskService.getTaskList(1,1,taskQueryWrapper);
        if (taskPage.getRecords().size() == 0){
            return;
        }
        Task task = taskPage.getRecords().get(0);
        // 更新任务状态
        task.setStatus(1);
        taskService.updateById(task);
        // 获取node信息
        Master node = masterService.getById(task.getNodeid());
        // 获取vm信息
        Vmhost vmhost = vmhostService.getById(task.getHostid());
        // 获取disk信息
        Map<Object,Object> diskMap = task.getParams();
        String os = diskMap.get("os").toString();
        log.info("[Task-ImportSystemDisk] 执行导入系统盘任务: NodeID:{} VM-ID:{}",node.getId(),task.getVmid());
        SshUtil sshUtil = new SshUtil(node.getHost(),node.getSshPort(),node.getSshUsername(),node.getSshPassword());
        try {
            sshUtil.connect();
            sshUtil.executeCommand("qm importdisk "+task.getVmid()+" /home/images/"+os+" "+vmhost.getStorage());
            sshUtil.disconnect();
        } catch (JSchException | InterruptedException e) {
            log.error("[Task-ImportSystemDisk] 导入系统盘任务: NodeID:{} VM-ID:{} 失败",node.getId(),task.getVmid());
            // 更新任务状态
            task.setStatus(3);
            // 添加错误信息
            task.setError(e.getMessage());
            taskService.updateById(task);
            e.printStackTrace();
        }


    }

    @Async
    @Scheduled(fixedDelay = 1000)
    public void monitorImportDiskCron(){
        QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("type",IMPORT_SYSTEM_DISK);
        queryWrapper.eq("status",1);
        Page<Task> taskPage = taskService.getTaskList(1,2,queryWrapper);
        // 判断是否没有任务
        if (taskPage.getRecords().size() == 0){
            return;
        }
        // 逐个任务检查
        for (Task task : taskPage.getRecords()) {
            long nowTime = System.currentTimeMillis();
            // 判断任务时间是否超过25分钟
            if (nowTime - task.getCreateDate() > 1000*60*25){
                // 修改任务状态为失败
                task.setStatus(3);
                task.setError("任务超时");
                taskService.updateById(task);
                log.error("[Task-ImportSystemDisk] 导入系统盘任务: NodeID:{} VM-ID:{} 失败",task.getNodeid(),task.getVmid());
                continue;
            }
            // 获取node信息
            Master node = masterService.getById(task.getNodeid());
            // 获取vm信息
            Vmhost vmhost = vmhostService.getById(task.getHostid());
            ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
            HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
            JSONObject vmInfo = masterService.getVmInfo(node.getId(),task.getVmid());
            // 如果存在unused0则表示导入成功
            if (vmInfo.getString("unused0")!=null){
                // 修改任务状态为进行中
                task.setStatus(2);
                taskService.updateById(task);
                HashMap<String, Object> params = new HashMap<>();
                params.put("scsihw", "virtio-scsi-pci");
                //params.put("scsihw", "lsi53c810");
                params.put("scsi0", vmInfo.getString("unused0"));
                try {
                    proxmoxApiUtil.putNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+task.getVmid()+"/config", params);
                } catch (Exception e) {
                    log.error("[Task-ImportSystemDisk] 导入系统盘任务: NodeID:{} VM-ID:{} 失败",task.getNodeid(),task.getVmid());
                    // 修改任务状态为失败
                    task.setStatus(3);
                    task.setError(e.getMessage());
                    taskService.updateById(task);
                    e.printStackTrace();
                    continue;
                }
                // 修改任务状态为成功
                task.setStatus(2);
                taskService.updateById(task);
                log.info("[Task-ImportSystemDisk] 导入系统盘任务: NodeID:{} VM-ID:{} 完成",task.getNodeid(),task.getVmid());
                // 创建修改系统盘大小任务
                log.info("[Task-UpdateSystemDisk] 添加创建修改系统盘大小任务: NodeID:{} VM-ID:{}",node.getId(),task.getVmid());
                long time = System.currentTimeMillis();
                Task updateSystemDiskTask = new Task();
                updateSystemDiskTask.setNodeid(node.getId());
                updateSystemDiskTask.setHostid(vmhost.getId());
                updateSystemDiskTask.setVmid(task.getVmid());
                updateSystemDiskTask.setType(UPDATE_SYSTEM_DISK_SIZE);
                updateSystemDiskTask.setStatus(0);
                updateSystemDiskTask.setParams(task.getParams());
                updateSystemDiskTask.setCreateDate(time);
                if (taskService.save(updateSystemDiskTask)){
                    log.info("[Task-UpdateSystemDisk] 添加创建修改系统盘大小任务: NodeID:{} VM-ID:{} 完成",node.getId(),task.getVmid());
                    // 增加虚拟机task
                    vmhost.getTask().put(String.valueOf(time), updateSystemDiskTask.getId());
                    vmhostService.updateById(vmhost);
                }else {
                    log.error("[Task-UpdateSystemDisk] 添加创建修改系统盘大小任务: NodeID:{} VM-ID:{} 失败",node.getId(),task.getVmid());
                }
            }
        }
    }

    @Async
    @Scheduled(fixedDelay = 1000)
    public void updateSystemDiskSizeCron(){
        QueryWrapper<Task> taskQueryWrapper = new QueryWrapper<>();
        taskQueryWrapper.eq("type",UPDATE_SYSTEM_DISK_SIZE);
        taskQueryWrapper.eq("status",0);
        Page<Task> taskPage = taskService.getTaskList(1,1,taskQueryWrapper);
        // 判断是否没有任务
        if (taskPage.getRecords().size() == 0){
            return;
        }
        // 逐个任务修改
        for (Task task : taskPage.getRecords()){
            // 修改任务状态为1
            task.setStatus(1);
            taskService.updateById(task);
            // 获取node信息
            Master node = masterService.getById(task.getNodeid());
            // 获取vm信息
            Vmhost vmhost = vmhostService.getById(task.getHostid());
            // 获取disk信息
            Map<Object,Object> diskMap = task.getParams();
            int size = diskMap.get("size") == null ? 40 : Integer.parseInt(diskMap.get("size").toString());
            ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
            HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
            HashMap<String, Object> params = new HashMap<>();
            params.put("disk","scsi0");
            params.put("size",size+"G");
            log.info("[Task-UpdateSystemDisk] 执行修改系统盘大小任务: NodeID:{} VM-ID:{} Size:{}G",node.getId(),task.getVmid(),size);
            try {
                proxmoxApiUtil.putNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+vmhost.getVmid()+"/resize", params);
            } catch (Exception e) {
                log.error("[Task-UpdateSystemDisk] 修改系统盘大小任务: NodeID:{} VM-ID:{} 失败",node.getId(),task.getVmid());
                // 修改任务状态为失败
                task.setStatus(3);
                task.setError(e.getMessage());
                taskService.updateById(task);
                e.printStackTrace();
                continue;
            }
            // 修改任务状态为成功
            task.setStatus(2);
            taskService.updateById(task);
            log.info("[Task-UpdateSystemDisk] 添加修改系统盘大小任务: NodeID:{} VM-ID:{} 完成",node.getId(),vmhost.getVmid());
            // 创建数据盘任务
            log.info("[Task-CreateDataDisk] 添加创建数据盘任务: NodeID:{} VM-ID:{}",node.getId(),task.getVmid());
            long time = System.currentTimeMillis();
            Task createDataDiskTask = new Task();
            createDataDiskTask.setNodeid(node.getId());
            createDataDiskTask.setHostid(vmhost.getId());
            createDataDiskTask.setVmid(task.getVmid());
            createDataDiskTask.setType(CREATE_DATA_DISK);
            createDataDiskTask.setStatus(0);
            createDataDiskTask.setParams(vmhost.getDataDisk());
            createDataDiskTask.setCreateDate(time);
            if (taskService.save(createDataDiskTask)){
                log.info("[Task-CreateDataDisk] 添加创建数据盘任务: NodeID:{} VM-ID:{} 完成",node.getId(),task.getVmid());
                // 增加虚拟机task
                vmhost.getTask().put(String.valueOf(time), createDataDiskTask.getId());
                vmhostService.updateById(vmhost);
            }else {
                log.error("[Task-CreateDataDisk] 添加创建数据盘任务: NodeID:{} VM-ID:{} 失败",node.getId(),task.getVmid());
                // 修改任务状态为失败
                task.setStatus(3);
                task.setError("创建数据盘任务失败");
                taskService.updateById(task);
            }
        }
    }

    @Async
    @Scheduled(fixedDelay = 1000)
    public void createDataDiskCron(){
        QueryWrapper<Task> taskQueryWrapper = new QueryWrapper<>();
        taskQueryWrapper.eq("type",CREATE_DATA_DISK);
        taskQueryWrapper.eq("status",0);
        Page<Task> taskPage = taskService.getTaskList(1,1,taskQueryWrapper);
        // 判断是否没有任务
        if (taskPage.getRecords().size() == 0){
            return;
        }
        Task task = taskPage.getRecords().get(0);
        // 修改任务状态为1
        task.setStatus(1);
        taskService.updateById(task);
        // 获取node信息
        Master node = masterService.getById(task.getNodeid());
        // 获取vm信息
        Vmhost vmhost = vmhostService.getById(task.getHostid());
        // 获取数据盘信息
        Map<Object,Object> dataDiskMap = task.getParams();
        // 判断是否为空
        if (dataDiskMap == null){
            return;
        }
        log.info("[Task-CreateDataDisk] 执行创建数据盘任务: NodeID:{} VM-ID:{}",node.getId(),task.getVmid());
        // 转换为JSONObject
        JSONObject dataDiskJson = new JSONObject(dataDiskMap);
        HashMap<String,Object> params = new HashMap<>();
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
        int num = 1;
        // 遍历dataDiskJson
        for (String key : dataDiskJson.keySet()){
            // 添加磁盘
            params.put("scsi"+num,vmhost.getStorage()+":"+dataDiskJson.get(key));
            num++;
        }
        try {
            proxmoxApiUtil.putNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+vmhost.getVmid()+"/config", params);
        } catch (Exception e) {
            log.error("[Task-CreateDataDisk] 创建数据盘任务: NodeID:{} VM-ID:{} 失败",node.getId(),task.getVmid());
            // 修改任务状态为失败
            task.setStatus(3);
            task.setError(e.getMessage());
            taskService.updateById(task);
            e.printStackTrace();
            return;
        }
        // 修改任务状态为成功
        task.setStatus(2);
        taskService.updateById(task);
        log.info("[Task-CreateDataDisk] 创建数据盘任务: NodeID:{} VM-ID:{} 完成",node.getId(),vmhost.getVmid());
        // 创建修改启动项任务
        log.info("[Task-UpdateBoot] 添加创建修改启动项任务: NodeID:{} VM-ID:{}",node.getId(),task.getVmid());
        long time = System.currentTimeMillis();
        Task updateBootDiskTask = new Task();
        updateBootDiskTask.setNodeid(node.getId());
        updateBootDiskTask.setHostid(vmhost.getId());
        updateBootDiskTask.setVmid(task.getVmid());
        updateBootDiskTask.setType(UPDATE_VM_BOOT);
        updateBootDiskTask.setStatus(0);
        Map<Object,Object> bootDiskMap = new HashMap<>();
        bootDiskMap.put("boot","order=scsi0;ide2;net0");
        updateBootDiskTask.setParams(bootDiskMap);
        updateBootDiskTask.setCreateDate(time);
        if (taskService.insertTask(updateBootDiskTask)){
            log.info("[Task-UpdateBoot] 添加创建修改启动项任务: NodeID:{} VM-ID:{} 完成",node.getId(),task.getVmid());
            // 增加虚拟机task
            vmhost.getTask().put(String.valueOf(time), updateBootDiskTask.getId());
            vmhostService.updateById(vmhost);
        }else {
            log.error("[Task-UpdateBoot] 添加创建修改启动项任务: NodeID:{} VM-ID:{} 失败",node.getId(),task.getVmid());
            // 修改任务状态为失败
            task.setStatus(3);
            task.setError("创建修改启动项任务失败");
            taskService.updateById(task);
        }
    }


}
