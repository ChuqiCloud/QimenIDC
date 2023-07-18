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

import static com.chuqiyun.proxmoxveams.constant.TaskType.*;

/**
 * @author mryunqi
 * @date 2023/7/1
 */
@Slf4j
@Component
@EnableScheduling
public class VmStatusCron {
    @Resource
    private MasterService masterService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private TaskService taskService;

    /**
    * @Author: mryunqi
    * @Description: 开机任务
    * @DateTime: 2023/7/18 22:31
    */
    @Async
    @Scheduled(fixedDelay = 1000)
    public void startVm() {
        QueryWrapper<Task> queryWrap = new QueryWrapper<>();
        queryWrap.eq("type", START_VM);
        queryWrap.eq("status", 0);
        Task task = taskService.getOne(queryWrap);
        if (task != null){
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
                return;
            }
            // 设置数据库中的vm状态为0
            vmhost.setStatus(0);
            vmhostService.updateById(vmhost);
            // 设置任务状态为2 2为执行完成
            task.setStatus(2);
            taskService.updateById(task);
            log.info("[Task-StartVm] 开机任务: NodeID:{} VM-ID:{} 完成",node.getId(),task.getVmid());
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 关机任务
    * @DateTime: 2023/7/18 22:31
    */
    @Async
    @Scheduled(fixedDelay = 1000)
    public void stopVm(){
        QueryWrapper<Task> queryWrap = new QueryWrapper<>();
        queryWrap.eq("type", STOP_VM);
        queryWrap.eq("status", 0);
        Task task = taskService.getOne(queryWrap);
        if (task != null){
            // 设置任务状态为1 1为正在执行
            task.setStatus(1);
            taskService.updateById(task);
            // 获取node信息
            Master node = masterService.getById(task.getNodeid());
            log.info("[Task-StopVm] 执行关机任务: NodeID:{} VM-ID:{}",node.getId(),task.getVmid());
            // 获取vm信息
            Vmhost vmhost = vmhostService.getById(task.getHostid());
            ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
            HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
            HashMap<String,Object> params = new HashMap<>();
            try {
                proxmoxApiUtil.postNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+task.getVmid()+"/status/stop", params);
            } catch (Exception e) {
                log.error("[Task-StopVm] 关机任务: NodeID:{} VM-ID:{} 失败",node.getId(),task.getVmid());
                // 修改任务状态为3 3为执行失败
                task.setStatus(3);
                task.setError(e.getMessage());
                taskService.updateById(task);
                e.printStackTrace();
                return;
            }
            // 设置数据库中的vm状态为1 1为关机
            vmhost.setStatus(1);
            vmhostService.updateById(vmhost);
            // 设置任务状态为2 2为执行完成
            task.setStatus(2);
            taskService.updateById(task);
            log.info("[Task-StopVm] 关机任务: NodeID:{} VM-ID:{} 完成",node.getId(),task.getVmid());
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 重启任务
    * @DateTime: 2023/7/18 22:38
    */
    @Async
    @Scheduled(fixedDelay = 1000)
    public void rebootVm(){
        QueryWrapper<Task> queryWrap = new QueryWrapper<>();
        queryWrap.eq("type", REBOOT_VM);
        queryWrap.eq("status", 0);
        Task task = taskService.getOne(queryWrap);
        if (task != null){
            // 设置任务状态为1 1为正在执行
            task.setStatus(1);
            taskService.updateById(task);
            // 获取node信息
            Master node = masterService.getById(task.getNodeid());
            log.info("[Task-RebootVm] 执行重启任务: NodeID:{} VM-ID:{}",node.getId(),task.getVmid());
            // 获取vm信息
            Vmhost vmhost = vmhostService.getById(task.getHostid());
            ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
            HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
            HashMap<String,Object> params = new HashMap<>();
            try {
                proxmoxApiUtil.postNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+task.getVmid()+"/status/reboot", params);
            } catch (Exception e) {
                log.error("[Task-RebootVm] 重启任务: NodeID:{} VM-ID:{} 失败",node.getId(),task.getVmid());
                // 修改任务状态为3 3为执行失败
                task.setStatus(3);
                task.setError(e.getMessage());
                taskService.updateById(task);
                e.printStackTrace();
                return;
            }
            // 设置数据库中的vm状态为0
            vmhost.setStatus(0);
            vmhostService.updateById(vmhost);
            // 设置任务状态为2 2为执行完成
            task.setStatus(2);
            taskService.updateById(task);
            log.info("[Task-RebootVm] 重启任务: NodeID:{} VM-ID:{} 完成",node.getId(),task.getVmid());
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 立刻停止任务
    * @DateTime: 2023/7/18 22:43
    */
    @Async
    @Scheduled(fixedDelay = 1000)
    public void stopVmNow(){
        QueryWrapper<Task> queryWrap = new QueryWrapper<>();
        queryWrap.eq("type", STOP_VM_FORCE);
        queryWrap.eq("status", 0);
        Task task = taskService.getOne(queryWrap);
        if (task != null){
            // 设置任务状态为1 1为正在执行
            task.setStatus(1);
            taskService.updateById(task);
            // 获取node信息
            Master node = masterService.getById(task.getNodeid());
            log.info("[Task-StopVmNow] 执行停止任务: NodeID:{} VM-ID:{}",node.getId(),task.getVmid());
            // 获取vm信息
            Vmhost vmhost = vmhostService.getById(task.getHostid());
            ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
            HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
            HashMap<String,Object> params = new HashMap<>();
            try {
                proxmoxApiUtil.postNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+task.getVmid()+"/status/shutdown", params);
            } catch (Exception e) {
                log.error("[Task-StopVmNow] 停止任务: NodeID:{} VM-ID:{} 失败",node.getId(),task.getVmid());
                // 修改任务状态为3 3为执行失败
                task.setStatus(3);
                task.setError(e.getMessage());
                taskService.updateById(task);
                e.printStackTrace();
                return;
            }
            // 设置数据库中的vm状态为1 1为关机
            vmhost.setStatus(1);
            vmhostService.updateById(vmhost);
            // 设置任务状态为2 2为执行完成
            task.setStatus(2);
            taskService.updateById(task);
            log.info("[Task-StopVmNow] 停止任务: NodeID:{} VM-ID:{} 完成",node.getId(),task.getVmid());
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 挂起任务
    * @DateTime: 2023/7/18 22:54
    */
    @Async
    @Scheduled(fixedDelay = 1000)
    public void suspendVm(){
        QueryWrapper<Task> queryWrap = new QueryWrapper<>();
        queryWrap.eq("type", SUSPEND_VM);
        queryWrap.eq("status", 0);
        Task task = taskService.getOne(queryWrap);
        if (task != null){
            // 设置任务状态为1 1为正在执行
            task.setStatus(1);
            taskService.updateById(task);
            // 获取node信息
            Master node = masterService.getById(task.getNodeid());
            log.info("[Task-SuspendVm] 执行挂起任务: NodeID:{} VM-ID:{}",node.getId(),task.getVmid());
            // 获取vm信息
            Vmhost vmhost = vmhostService.getById(task.getHostid());
            ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
            HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
            HashMap<String,Object> params = new HashMap<>();
            try {
                proxmoxApiUtil.postNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+task.getVmid()+"/status/suspend", params);
            } catch (Exception e) {
                log.error("[Task-SuspendVm] 挂起任务: NodeID:{} VM-ID:{} 失败",node.getId(),task.getVmid());
                // 修改任务状态为3 3为执行失败
                task.setStatus(3);
                task.setError(e.getMessage());
                taskService.updateById(task);
                e.printStackTrace();
                return;
            }
            // 设置数据库中的vm状态为2 2为挂起
            vmhost.setStatus(2);
            vmhostService.updateById(vmhost);
            // 设置任务状态为2 2为执行完成
            task.setStatus(2);
            taskService.updateById(task);
            log.info("[Task-SuspendVm] 挂起任务: NodeID:{} VM-ID:{} 完成",node.getId(),task.getVmid());
        }
    }
    
    /**
    * @Author: mryunqi
    * @Description: 挂起恢复任务
    * @DateTime: 2023/7/18 23:09
    */
    @Async
    @Scheduled(fixedDelay = 1000)
    public void resumeVm(){
        QueryWrapper<Task> queryWrap = new QueryWrapper<>();
        queryWrap.eq("type", RESUME_VM);
        queryWrap.eq("status", 0);
        Task task = taskService.getOne(queryWrap);
        if (task != null){
            // 设置任务状态为1 1为正在执行
            task.setStatus(1);
            taskService.updateById(task);
            // 获取node信息
            Master node = masterService.getById(task.getNodeid());
            log.info("[Task-ResumeVm] 执行恢复任务: NodeID:{} VM-ID:{}",node.getId(),task.getVmid());
            // 获取vm信息
            Vmhost vmhost = vmhostService.getById(task.getHostid());
            ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
            HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
            HashMap<String,Object> params = new HashMap<>();
            try {
                proxmoxApiUtil.postNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+task.getVmid()+"/status/resume", params);
            } catch (Exception e) {
                log.error("[Task-ResumeVm] 恢复任务: NodeID:{} VM-ID:{} 失败",node.getId(),task.getVmid());
                // 修改任务状态为3 3为执行失败
                task.setStatus(3);
                task.setError(e.getMessage());
                taskService.updateById(task);
                e.printStackTrace();
                return;
            }
            // 设置数据库中的vm状态为3 3为恢复中
            vmhost.setStatus(3);
            vmhostService.updateById(vmhost);
            // 设置任务状态为2 2为执行完成
            task.setStatus(2);
            taskService.updateById(task);
            log.info("[Task-ResumeVm] 恢复任务: NodeID:{} VM-ID:{} 完成",node.getId(),task.getVmid());
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 暂停虚拟机任务
    * @DateTime: 2023/7/18 23:20
    */
    @Async
    @Scheduled(fixedDelay = 1000)
    public void pauseVm(){
        QueryWrapper<Task> queryWrap = new QueryWrapper<>();
        // 暂停为挂起操作
        queryWrap.eq("type", SUSPEND_VM);
        queryWrap.eq("status", 0);
        Task task = taskService.getOne(queryWrap);
        if (task != null){
            // 设置任务状态为1 1为正在执行
            task.setStatus(1);
            taskService.updateById(task);
            // 获取node信息
            Master node = masterService.getById(task.getNodeid());
            log.info("[Task-PauseVm] 执行暂停任务: NodeID:{} VM-ID:{}",node.getId(),task.getVmid());
            // 获取vm信息
            Vmhost vmhost = vmhostService.getById(task.getHostid());
            ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
            HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
            HashMap<String,Object> params = new HashMap<>();
            try {
                proxmoxApiUtil.postNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+task.getVmid()+"/status/suspend", params);
            } catch (Exception e) {
                log.error("[Task-PauseVm] 暂停任务: NodeID:{} VM-ID:{} 失败",node.getId(),task.getVmid());
                // 修改任务状态为3 3为执行失败
                task.setStatus(3);
                task.setError(e.getMessage());
                taskService.updateById(task);
                e.printStackTrace();
                return;
            }
            // 设置数据库中的vm状态为4 4为暂停
            vmhost.setStatus(4);
            vmhostService.updateById(vmhost);
            // 设置任务状态为2 2为执行完成
            task.setStatus(2);
            taskService.updateById(task);
            log.info("[Task-PauseVm] 暂停任务: NodeID:{} VM-ID:{} 完成",node.getId(),task.getVmid());
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 恢复暂停任务
    * @DateTime: 2023/7/18 23:24
    */
    @Async
    @Scheduled(fixedDelay = 1000)
    public void unpauseVm(){
        QueryWrapper<Task> queryWrap = new QueryWrapper<>();
        queryWrap.eq("type", RESUME_VM);
        queryWrap.eq("status", 0);
        Task task = taskService.getOne(queryWrap);
        if (task != null){
            // 设置任务状态为1 1为正在执行
            task.setStatus(1);
            taskService.updateById(task);
            // 获取node信息
            Master node = masterService.getById(task.getNodeid());
            log.info("[Task-UnpauseVm] 执行恢复暂停任务: NodeID:{} VM-ID:{}",node.getId(),task.getVmid());
            // 获取vm信息
            Vmhost vmhost = vmhostService.getById(task.getHostid());
            ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
            HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
            HashMap<String,Object> params = new HashMap<>();
            try {
                proxmoxApiUtil.postNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+task.getVmid()+"/status/resume", params);
            } catch (Exception e) {
                log.error("[Task-UnpauseVm] 恢复暂停任务: NodeID:{} VM-ID:{} 失败",node.getId(),task.getVmid());
                // 修改任务状态为3 3为执行失败
                task.setStatus(3);
                task.setError(e.getMessage());
                taskService.updateById(task);
                e.printStackTrace();
                return;
            }
            // 设置数据库中的vm状态为3 3为挂起
            vmhost.setStatus(3);
            vmhostService.updateById(vmhost);
            // 设置任务状态为2 2为执行完成
            task.setStatus(2);
            taskService.updateById(task);
            log.info("[Task-UnpauseVm] 恢复暂停任务: NodeID:{} VM-ID:{} 完成",node.getId(),task.getVmid());
        }
    }

}
