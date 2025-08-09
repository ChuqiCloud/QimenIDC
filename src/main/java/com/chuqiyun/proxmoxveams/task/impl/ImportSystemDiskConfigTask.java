package com.chuqiyun.proxmoxveams.task.impl;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
import com.chuqiyun.proxmoxveams.constant.TaskStatus;
import com.chuqiyun.proxmoxveams.constant.TaskType;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.*;
import com.chuqiyun.proxmoxveams.task.TaskHandler;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ImportSystemDiskConfigTask implements TaskHandler {

    @Resource
    private TaskService taskService;

    @Resource
    private MasterService masterService;

    @Resource
    private VmhostService vmhostService;

    @Resource
    private OsService osService;

    @Resource
    private ConfigService configService;
    @Override
    public void process(Task task) {
        // 更新任务状态为1 (正在执行)
        task.setStatus(TaskStatus.RUNNING);
        taskService.updateById(task);

        // 获取node信息
        Master node = masterService.getById(task.getNodeid());
        // 获取vm信息
        Vmhost vmhost = vmhostService.getById(task.getHostid());
        long nowTime = System.currentTimeMillis();
        // 判断任务时间是否超过25分钟
        if (nowTime - task.getCreateDate() > 1000*60*25){
            // 修改任务状态为失败
            task.setStatus(TaskStatus.FAILURE);
            task.setError("任务超时");
            taskService.updateById(task);
            UnifiedLogger.warn(UnifiedLogger.LogType.TASK_IMPORT_SYSTEM_DISK,"导入系统盘任务: NodeID:{} VM-ID:{} 失败",task.getNodeid(),task.getVmid());
            return;
        }
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
        JSONObject vmInfo = masterService.getVmInfo(node.getId(),vmhost.getVmid());
        //System.out.println(vmInfo);
        // 如果存在unused未配置的磁盘，则优先配置最新的磁盘
        Pattern pattern = Pattern.compile("^unused(\\d+)$");
        int maxNum = -1;
        List<String> diskKeyList = new ArrayList<>();
        // 获取所有匹配的key
        String latestKey = null;
        for (String key : vmInfo.keySet()) {
            // 判断key是否匹配
            Matcher matcher = pattern.matcher(key);
            if (matcher.matches()) {
                // 获取数字部分
                int num = Integer.parseInt(matcher.group(1));
                // 更新最大数字
                if (num > maxNum) {
                    maxNum = num;
                    latestKey = key;
                }
                diskKeyList.add(key);
            }
        }
        // 暂时将任务状态改为0

        if (latestKey == null) {
            task.setStatus(TaskStatus.WAITING);
            taskService.updateById(task);
            return;
        }
        //System.out.println(latestKey);
        //System.out.println(diskKeyList);
        HashMap<String, Object> params = new HashMap<>();

        // 系统盘大小
        String diskSize = vmhost.getSystemDiskSize() + "G";
        // 系统磁盘读取长效限制 单位mb/s
        Integer mbpsRd = vmhost.getMbpsRd();
        // 系统磁盘写入长效限制 单位mb/s
        Integer mbpsWr = vmhost.getMbpsWr();
        // 系统磁盘读取突发限制 单位mb/s
        Integer mbpsRdMax = vmhost.getMbpsRdMax();
        // 系统磁盘写入突发限制 单位mb/s
        Integer mbpsWrMax = vmhost.getMbpsWrMax();
        // 系统磁盘iops读取长效限制 单位ops/s
        Integer iopsRd = vmhost.getIopsRd();
        // 系统磁盘iops写入长效限制 单位ops/s
        Integer iopsWr = vmhost.getIopsWr();
        // 系统磁盘iops读取突发限制 单位ops/s
        Integer iopsRdMax = vmhost.getIopsRdMax();
        // 系统磁盘iops写入突发限制 单位ops/s
        Integer iopsWrMax = vmhost.getIopsWrMax();

        String paramsValues = vmInfo.getString(latestKey);
        // 向paramsValues添加其他参数
        paramsValues += "," + "size=" + diskSize;
        paramsValues += "," + "mbps=" + mbpsRd + "," + mbpsWr + "," + mbpsRdMax + "," + mbpsWrMax;
        // 挂载磁盘
        //HashMap<String, Object> params = new HashMap<>();
        //params.put("scsi"+maxNum,vmInfo.getString(latestKey));
        params.put("scsi"+maxNum,paramsValues);
        try {
            proxmoxApiUtil.putNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+task.getVmid()+"/config", params);
        } catch (Exception e) {
            UnifiedLogger.warn(UnifiedLogger.LogType.TASK_IMPORT_SYSTEM_DISK,"导入系统盘任务: NodeID:{} VM-ID:{} 失败",task.getNodeid(),task.getVmid());
            // 修改任务状态为失败
            task.setStatus(TaskStatus.FAILURE);
            task.setError(e.getMessage());
            taskService.updateById(task);
            e.printStackTrace();
            return;
        }
        // 修改任务状态为成功
        task.setStatus(TaskStatus.SUCCESS);
        taskService.updateById(task);
        UnifiedLogger.log(UnifiedLogger.LogType.TASK_IMPORT_SYSTEM_DISK,"导入系统盘任务: NodeID:{} VM-ID:{} 成功",task.getNodeid(),task.getVmid());
    }

    @Override
    public int getType() {
        return TaskType.IMPORT_SYSTEM_DISK_NEW;
    }
}
