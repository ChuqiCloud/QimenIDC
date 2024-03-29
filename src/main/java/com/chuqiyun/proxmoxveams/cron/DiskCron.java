package com.chuqiyun.proxmoxveams.cron;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Os;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.*;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import com.chuqiyun.proxmoxveams.utils.SshUtil;
import com.chuqiyun.proxmoxveams.utils.VmUtil;
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
    @Resource
    private OsService osService;
    @Resource
    private ConfigService configService;
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
        Os osInfo = osService.selectOsByFileName(os);
        String path = osInfo.getPath();
        // 删除结尾的/
        if (path.endsWith("/")){
            path = path.substring(0,path.length()-1);
        }
        UnifiedLogger.log(UnifiedLogger.LogType.TASK_IMPORT_SYSTEM_DISK,"执行导入系统盘任务: NodeID:{} VM-ID:{}",node.getId(),task.getVmid());
        /*SshUtil sshUtil = new SshUtil(node.getHost(),node.getSshPort(),node.getSshUsername(),node.getSshPassword());
        try {
            sshUtil.connect();
            sshUtil.executeCommand("qm importdisk "+task.getVmid()+" "+path+"/"+os+" "+vmhost.getStorage());
            sshUtil.disconnect();
        } catch (JSchException | InterruptedException e) {
            UnifiedLogger.warn(UnifiedLogger.LogType.TASK_IMPORT_SYSTEM_DISK,"导入系统盘任务: NodeID:{} VM-ID:{} 失败",node.getId(),task.getVmid());
            // 更新任务状态
            task.setStatus(3);
            // 添加错误信息
            task.setError(e.getMessage());
            taskService.updateById(task);
            e.printStackTrace();
        }*/
        // 调用被控api导入系统盘
        try {
            boolean result = ClientApiUtil.importDisk(node.getHost(),node.getControllerPort(),configService.getToken(), Long.valueOf(task.getVmid()),path+"/"+os,vmhost.getStorage());

            if (!result){
                UnifiedLogger.warn(UnifiedLogger.LogType.TASK_IMPORT_SYSTEM_DISK,"导入系统盘任务: NodeID:{} VM-ID:{} 失败",node.getId(),task.getVmid());
                // 更新任务状态
                task.setStatus(3);
                // 添加错误信息
                task.setError("导入系统盘失败");
                taskService.updateById(task);
            }
        } catch (Exception e) {
            UnifiedLogger.warn(UnifiedLogger.LogType.TASK_IMPORT_SYSTEM_DISK,"导入系统盘任务: NodeID:{} VM-ID:{} 失败",node.getId(),task.getVmid());
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
                UnifiedLogger.warn(UnifiedLogger.LogType.TASK_IMPORT_SYSTEM_DISK,"导入系统盘任务: NodeID:{} VM-ID:{} 失败",task.getNodeid(),task.getVmid());
                continue;
            }
            // 获取node信息
            Master node = masterService.getById(task.getNodeid());
            // 获取vm信息
            //Vmhost vmhost = vmhostService.getById(task.getHostid());
            ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
            HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
            JSONObject vmInfo = masterService.getVmInfo(node.getId(),task.getVmid());
            // 如果存在unused0则表示导入成功
            if (vmInfo.getString("unused0")!=null){
                // 修改任务状态为进行中
                task.setStatus(2);
                taskService.updateById(task);
                // 获取vm信息
                Vmhost vmhost = vmhostService.getById(task.getHostid());
                HashMap<String, Object> params = new HashMap<>();
                params.put("scsihw", "virtio-scsi-pci");
                //params.put("scsihw", "lsi53c810");
                //params.put("scsi0", VmUtil.getSystemDiskParams(vmhost, vmInfo.getString("unused0"),false,true));
                params.put("scsi0", vmInfo.getString("unused0"));
                try {
                    proxmoxApiUtil.putNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+task.getVmid()+"/config", params);
                } catch (Exception e) {
                    UnifiedLogger.warn(UnifiedLogger.LogType.TASK_IMPORT_SYSTEM_DISK,"导入系统盘任务: NodeID:{} VM-ID:{} 失败",task.getNodeid(),task.getVmid());
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
                UnifiedLogger.log(UnifiedLogger.LogType.TASK_IMPORT_SYSTEM_DISK,"导入系统盘任务: NodeID:{} VM-ID:{} 完成",task.getNodeid(),task.getVmid());
            }
        }
    }

    /**
     * 修改系统盘大小
     */
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
            int size = Integer.parseInt(diskMap.get("systemDiskSize").toString());
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
    }

    /**
     * 修改系统盘IO限制
     */
    @Async
    @Scheduled(fixedDelay = 500)
    public void updateSystemDiskIOLimitCron() {
        QueryWrapper<Task> taskQueryWrapper = new QueryWrapper<>();
        taskQueryWrapper.eq("type", UPDATE_IO_SYSTEM_DISK);
        taskQueryWrapper.eq("status", 0);
        Page<Task> taskPage = taskService.getTaskList(1, 1, taskQueryWrapper);
        // 判断是否没有任务
        if (taskPage.getRecords().size() == 0) {
            return;
        }
        // 逐个任务修改
        for (Task task : taskPage.getRecords()) {
            // 修改任务状态为1
            task.setStatus(1);
            taskService.updateById(task);
            // 获取node信息
            Master node = masterService.getById(task.getNodeid());
            // 获取vm信息
            Vmhost vmhost = vmhostService.getById(task.getHostid());

            JSONObject vmInfo = masterService.getVmInfo(node.getId(),vmhost.getVmid()); // 获取vm信息

            // 判断是否存在'scsi0'磁盘
            if (!vmInfo.containsKey("scsi0")){
                //log.error("[Task-UpdateSystemDiskIOLimit] 执行修改系统盘IO限制任务: NodeID:{} VM-ID:{} 失败,不存在'scsi0'磁盘", node.getId(), task.getVmid());
                UnifiedLogger.log(UnifiedLogger.LogType.UpdateSystemDiskIOLimit, "执行修改系统盘IO限制任务: NodeID:{} VM-ID:{}", node.getId(), task.getVmid());
                // 修改任务状态为失败
                task.setStatus(3);
                task.setError("不存在系统磁盘");
                taskService.updateById(task);
                continue;
            }

            // 系统盘信息
            String diskInfo = vmInfo.getString("scsi0");

            ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
            HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
            HashMap<String, Object> params = new HashMap<>();
            params.put("scsi0", VmUtil.getSystemDiskParams(vmhost, diskInfo,false));
            //log.info("[Task-UpdateSystemDiskIOLimit] 执行修改系统盘IO限制任务: NodeID:{} VM-ID:{}", node.getId(), task.getVmid());
            UnifiedLogger.log(UnifiedLogger.LogType.UpdateSystemDiskIOLimit, "执行修改系统盘IO限制任务: NodeID:{} VM-ID:{}", node.getId(), task.getVmid());
            try {
                proxmoxApiUtil.postNodeApi(node, authentications, "/nodes/" + node.getNodeName() + "/qemu/" + vmhost.getVmid() + "/config", params);
            } catch (Exception e) {
                //log.error("[Task-UpdateSystemDiskIOLimit] 修改系统盘IO限制任务: NodeID:{} VM-ID:{} 失败", node.getId(), task.getVmid());
                UnifiedLogger.log(UnifiedLogger.LogType.UpdateSystemDiskIOLimit, "修改系统盘IO限制任务: NodeID:{} VM-ID:{} 失败", node.getId(), task.getVmid());
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
            //log.info("[Task-UpdateSystemDiskIOLimit] 修改系统盘IO限制任务: NodeID:{} VM-ID:{} 完成", node.getId(), vmhost.getVmid());
            UnifiedLogger.log(UnifiedLogger.LogType.UpdateSystemDiskIOLimit, "修改系统盘IO限制任务: NodeID:{} VM-ID:{} 完成", node.getId(), vmhost.getVmid());
        }

    }

}
