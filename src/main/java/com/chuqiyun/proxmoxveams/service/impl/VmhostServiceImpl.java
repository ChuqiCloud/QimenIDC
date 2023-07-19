package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.VmhostDao;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.VmParams;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.utils.TimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;

import static com.chuqiyun.proxmoxveams.constant.TaskType.*;

/**
 * (Vmhost)表服务实现类
 *
 * @author mryunqi
 * @since 2023-06-21 15:11:18
 */
@Slf4j
@Service("vmhostService")
public class VmhostServiceImpl extends ServiceImpl<VmhostDao, Vmhost> implements VmhostService {
    @Resource
    private MasterService masterService;
    @Resource
    private TaskService taskService;
    /**
    * @Author: mryunqi
    * @Description: 根据虚拟机id获取虚拟机实例信息
    * @DateTime: 2023/6/22 1:37
    */
    @Override
    public Vmhost getVmhostByVmId(int vmId) {
        return this.getOne(new QueryWrapper<Vmhost>().eq("vmid",vmId));
    }

    /**
    * @Author: mryunqi
    * @Description: 添加虚拟机实例信息
    * @DateTime: 2023/6/21 23:54
    */
    @Override
    public Integer addVmhost(int vmId,VmParams vmParams) {
        Vmhost vmhost = new Vmhost();
        vmhost.setNodeid(vmParams.getNodeid());
        vmhost.setVmid(vmId);
        vmhost.setName(vmParams.getHostname());
        vmhost.setCores(vmParams.getCores());
        vmhost.setMemory(vmParams.getMemory());
        vmhost.setStorage(vmParams.getStorage());
        vmhost.setSystemDiskSize(vmParams.getSystemDiskSize());
        vmhost.setDataDisk(vmParams.getDataDisk());
        vmhost.setBridge(vmParams.getBridge());
        vmhost.setOs(vmParams.getOs());
        vmhost.setBandwidth(vmParams.getBandwidth());
        vmhost.setIpConfig(vmParams.getIpConfig());
        if (vmParams.getNested() == null || !vmParams.getNested()) {
            vmhost.setNested(0);
        }
        else {
            vmhost.setNested(1);
        }
        vmhost.setTask(vmParams.getTask());
        vmhost.setCreateTime(System.currentTimeMillis());
        // 判断到期时间是否为空
        if (vmParams.getExpirationTime() == null) {
            // 时间设定为99年后到期
            vmhost.setExpirationTime(System.currentTimeMillis()+315360000000L);
        }
        // 判断到期时间是否为10位
        else if (vmParams.getExpirationTime().toString().length() == 10) {
            // 将时间转换为13位
            vmhost.setExpirationTime(TimeUtil.tenToThirteen(vmParams.getExpirationTime()));
        }
        else{
            vmhost.setExpirationTime(vmParams.getExpirationTime());
        }
        // 返回id
        return this.save(vmhost) ? vmhost.getId() : null;
    }

    /**
    * @Author: mryunqi
    * @Description: 虚拟机电源管理
    * @DateTime: 2023/7/18 16:57
    * @Params: Integer hostId 虚拟机ID, String action 操作类型
    * @Return HashMap<String,Object> 返回操作结果
    */
    @Override
    public HashMap<String,Object> power(Integer hostId, String action) {
        HashMap<String,Object> result = new HashMap<>();
        // 获取虚拟机实例信息
        Vmhost vmhost = this.getById(hostId);
        // 获取虚拟机id
        int vmId = vmhost.getVmid();
        // 获取节点id
        int nodeId = vmhost.getNodeid();
        // 获取虚拟机状态
        int vmStatus = vmhost.getStatus();
        // vmStatus状态有0=运行中、1=已关机、2=挂起、3=恢复中、4=暂停
        // action类型有start=开机、stop=关机、reboot=重启、shutdown=强制关机、suspend=挂起、resume=恢复、pause=暂停、unpause=恢复
        switch (action) {
            case "start": {
                // 判断虚拟机是否被暂停
                if (vmStatus == 4){
                    result.put("status", false);
                    result.put("msg", "虚拟机已暂停，无法关机");
                }
                // 判断虚拟机状态是否为已停止
                if (vmStatus == 0 || vmStatus == 3) {
                    result.put("status", true);
                    // 直接返回true
                }
                else {
                    // 创建开机任务
                    Task vmStartTask = new Task();
                    vmStartTask.setNodeid(nodeId);
                    vmStartTask.setVmid(vmId);
                    vmStartTask.setHostid(hostId);
                    vmStartTask.setType(START_VM);
                    vmStartTask.setStatus(0);
                    vmStartTask.setCreateDate(System.currentTimeMillis());
                    // 保存任务
                    if (taskService.save(vmStartTask)) {
                        log.info("[Task-StartVm] 开机任务创建成功: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", true);
                        // 直接返回true
                    }
                    else {
                        log.info("[Task-StartVm] 开机任务创建失败: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", false);
                        result.put("msg", "开机任务创建失败");
                    }
                }
                return result;
            }
            case "stop": {
                // 判断虚拟机是否被暂停
                if (vmStatus == 4){
                    result.put("status", false);
                    result.put("msg", "虚拟机已暂停，无法关机");
                }
                if (vmStatus == 1 || vmStatus == 2) {
                    result.put("status", true);
                    // 直接返回true
                }
                else {
                    Task vmStopTask = new Task();
                    vmStopTask.setNodeid(nodeId);
                    vmStopTask.setVmid(vmId);
                    vmStopTask.setHostid(hostId);
                    vmStopTask.setType(STOP_VM);
                    vmStopTask.setStatus(0);
                    vmStopTask.setCreateDate(System.currentTimeMillis());
                    if (taskService.save(vmStopTask)) {
                        log.info("[Task-StopVm] 关机任务创建成功: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", true);
                        // 直接返回true
                    }
                    else {
                        log.info("[Task-StopVm] 关机任务创建失败: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", false);
                        result.put("msg", "关机任务创建失败");
                    }
                }
                return result;
            }
            case "reboot": {
                // 判断虚拟机状态是否为暂停
                if (vmStatus == 4) {
                    result.put("status", false);
                    result.put("msg", "虚拟机已暂停，无法重启");
                }
                else {
                    Task vmRebootTask = new Task();
                    vmRebootTask.setNodeid(nodeId);
                    vmRebootTask.setVmid(vmId);
                    vmRebootTask.setHostid(hostId);
                    vmRebootTask.setType(REBOOT_VM);
                    vmRebootTask.setStatus(0);
                    vmRebootTask.setCreateDate(System.currentTimeMillis());
                    if (taskService.save(vmRebootTask)) {
                        log.info("[Task-RebootVm] 重启任务创建成功: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", true);
                        // 直接返回true
                    }
                    else {
                        log.info("[Task-RebootVm] 重启任务创建失败: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", false);
                        result.put("msg", "重启任务创建失败");
                    }
                }
                return result;
            }
            case "shutdown":{
                // 判断虚拟机状态是否为暂停
                if (vmStatus == 4) {
                    // 调用节点接口关机
                    result.put("status", false);
                    result.put("msg", "虚拟机已暂停，无法关机");
                }
                else {
                    Task vmShutdownTask = new Task();
                    vmShutdownTask.setNodeid(nodeId);
                    vmShutdownTask.setVmid(vmId);
                    vmShutdownTask.setHostid(hostId);
                    vmShutdownTask.setType(STOP_VM_FORCE);
                    vmShutdownTask.setStatus(0);
                    vmShutdownTask.setCreateDate(System.currentTimeMillis());
                    if (taskService.save(vmShutdownTask)) {
                        log.info("[Task-ShutdownVm] 强制关机任务创建成功: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", true);
                        // 直接返回true
                    }
                    else {
                        log.info("[Task-ShutdownVm] 强制关机任务创建失败: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", false);
                        result.put("msg", "强制关机任务创建失败");
                    }
                }
                return result;
            }
            case "suspend": {
                // 判断虚拟机状态是否为暂停
                if (vmStatus == 4) {
                    result.put("status", false);
                    result.put("msg", "虚拟机已暂停，无法挂起");
                }
                else {
                    Task vmSuspendTask = new Task();
                    vmSuspendTask.setNodeid(nodeId);
                    vmSuspendTask.setVmid(vmId);
                    vmSuspendTask.setHostid(hostId);
                    vmSuspendTask.setType(SUSPEND_VM);
                    vmSuspendTask.setStatus(0);
                    vmSuspendTask.setCreateDate(System.currentTimeMillis());
                    if (taskService.save(vmSuspendTask)) {
                        log.info("[Task-SuspendVm] 挂起任务创建成功: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", true);
                        // 直接返回true
                    }
                    else {
                        log.info("[Task-SuspendVm] 挂起任务创建失败: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", false);
                        result.put("msg", "挂起任务创建失败");
                    }
                }
                return result;
            }
            case "resume": {
                // 判断虚拟机状态是否为暂停
                if (vmStatus == 4) {
                    result.put("status", false);
                    result.put("msg", "虚拟机未暂停，无法恢复");
                }
                else {
                    Task vmResumeTask = new Task();
                    vmResumeTask.setNodeid(nodeId);
                    vmResumeTask.setVmid(vmId);
                    vmResumeTask.setHostid(hostId);
                    vmResumeTask.setType(RESUME_VM);
                    vmResumeTask.setStatus(0);
                    vmResumeTask.setCreateDate(System.currentTimeMillis());
                    if (taskService.save(vmResumeTask)) {
                        log.info("[Task-ResumeVm] 恢复任务创建成功: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", true);
                        // 直接返回true
                    }
                    else {
                        log.info("[Task-ResumeVm] 恢复任务创建失败: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", false);
                        result.put("msg", "恢复任务创建失败");
                    }
                }
                return result;
            }
            case "pause":{
                Task vmPauseTask = new Task();
                vmPauseTask.setNodeid(nodeId);
                vmPauseTask.setVmid(vmId);
                vmPauseTask.setHostid(hostId);
                vmPauseTask.setType(PAUSE_VM);
                vmPauseTask.setStatus(0);
                vmPauseTask.setCreateDate(System.currentTimeMillis());
                if (taskService.save(vmPauseTask)) {
                    log.info("[Task-PauseVm] 暂停任务创建成功: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                    result.put("status", true);
                    // 直接返回true
                }
                else {
                    log.info("[Task-PauseVm] 暂停任务创建失败: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                    result.put("status", false);
                    result.put("msg", "暂停任务创建失败");
                }
                return result;
            }
            case "unpause":{
                // 判断虚拟机状态是否为暂停
                if (vmStatus != 4) {
                    result.put("status", false);
                    result.put("msg", "虚拟机未暂停，无法恢复");
                }
                else {
                    Task vmUnpauseTask = new Task();
                    vmUnpauseTask.setNodeid(nodeId);
                    vmUnpauseTask.setVmid(vmId);
                    vmUnpauseTask.setHostid(hostId);
                    vmUnpauseTask.setType(UNPAUSE_VM);
                    vmUnpauseTask.setStatus(0);
                    vmUnpauseTask.setCreateDate(System.currentTimeMillis());
                    if (taskService.save(vmUnpauseTask)) {
                        log.info("[Task-UnpauseVm] 恢复任务创建成功: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", true);
                        // 直接返回true
                    }
                    else {
                        log.info("[Task-UnpauseVm] 恢复任务创建失败: NodeId: " + nodeId + ",VmId: " + vmId + ",HostId: " + hostId);
                        result.put("status", false);
                        result.put("msg", "恢复任务创建失败");
                    }
                }
                return result;
            }

            default: {
                result.put("status", false);
                result.put("msg", "未知操作");
                return result;
            }
        }
    }
}

