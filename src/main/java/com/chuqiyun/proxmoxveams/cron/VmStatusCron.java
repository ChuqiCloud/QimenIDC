package com.chuqiyun.proxmoxveams.cron;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
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
import java.util.List;

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
    @Scheduled(fixedDelay = 2000)
    public void startVm() {
        QueryWrapper<Task> queryWrap = new QueryWrapper<>();
        queryWrap.eq("type", START_VM);
        queryWrap.eq("status", 0);
        queryWrap.orderByAsc("create_date");
        queryWrap.last("LIMIT 1");
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
            if(vmhost.getStatus() == 6 || vmhost.getStatus() == 13)
            {
                log.error("[Task-StartVm] 开机任务: NodeID:{} VM-ID:{} 失败，创建/重装系统不允许开机！",node.getId(),task.getVmid());
                // 修改任务状态为3 3为执行失败
                task.setStatus(3);
                task.setError("创建/重装系统不允许开机！");
                taskService.updateById(task);
                return;
            }
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
    @Scheduled(fixedDelay = 2000)
    public void stopVm(){
        QueryWrapper<Task> queryWrap = new QueryWrapper<>();
        queryWrap.eq("type", STOP_VM);
        queryWrap.eq("status", 0);
        queryWrap.orderByAsc("create_date");
        queryWrap.last("LIMIT 1");
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
            if(vmhost.getStatus() == 6 || vmhost.getStatus() == 13)
            {
                log.error("[Task-StartVm] 关机任务: NodeID:{} VM-ID:{} 失败，创建/重装系统不允许开机！",node.getId(),task.getVmid());
                // 修改任务状态为3 3为执行失败
                task.setStatus(3);
                task.setError("创建/重装系统不允许关机！");
                taskService.updateById(task);
                return;
            }
            ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
            HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
            HashMap<String,Object> params = new HashMap<>();
            params.put("forceStop",true);
            try {
                proxmoxApiUtil.postNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+task.getVmid()+"/status/shutdown", params);
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
    @Scheduled(fixedDelay = 2000)
    public void rebootVm(){
        QueryWrapper<Task> queryWrap = new QueryWrapper<>();
        queryWrap.eq("type", REBOOT_VM);
        queryWrap.eq("status", 0);
        queryWrap.orderByAsc("create_date");
        queryWrap.last("LIMIT 1");
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
            if(vmhost.getStatus() == 6 || vmhost.getStatus() == 13)
            {
                log.error("[Task-RebootVm] 重启任务: NodeID:{} VM-ID:{} 失败，创建/重装系统不允许操作！",node.getId(),task.getVmid());
                // 修改任务状态为3 3为执行失败
                task.setStatus(3);
                task.setError("创建/重装系统不允许重启！");
                taskService.updateById(task);
                return;
            }
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
    @Scheduled(fixedDelay = 2000)
    public void stopVmNow(){
        QueryWrapper<Task> queryWrap = new QueryWrapper<>();
        queryWrap.eq("type", STOP_VM_FORCE);
        queryWrap.eq("status", 0);
        queryWrap.orderByAsc("create_date");
        queryWrap.last("LIMIT 1");
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
            // 强制停止
            //params.put("forceStop",true);

            try {
                proxmoxApiUtil.postNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+task.getVmid()+"/status/stop", params);
            } catch (Exception e) {
                log.error("[Task-StopVmNow] 停止任务: NodeID:{} VM-ID:{} 失败",node.getId(),task.getVmid());
                // 修改任务状态为3 3为执行失败
                task.setStatus(3);
                task.setError(e.getMessage());
                taskService.updateById(task);
                e.printStackTrace();
                return;
            }
            // 设置数据库中的vm状态为1 1为关机 非重装任务才执行
            if (vmhost.getStatus() != 13) {
                vmhost.setStatus(1);
                vmhostService.updateById(vmhost);
            }
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
    @Scheduled(fixedDelay = 2000)
    public void suspendVm(){
        QueryWrapper<Task> queryWrap = new QueryWrapper<>();
        queryWrap.eq("type", SUSPEND_VM);
        queryWrap.eq("status", 0);
        queryWrap.orderByAsc("create_date");
        queryWrap.last("LIMIT 1");
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
            if(vmhost.getStatus() == 6 || vmhost.getStatus() == 13)
            {
                log.error("[Task-SuspendVm] 挂起任务: NodeID:{} VM-ID:{} 失败，创建/重装系统不允许操作！",node.getId(),task.getVmid());
                // 修改任务状态为3 3为执行失败
                task.setStatus(3);
                task.setError("创建/重装系统不允许挂起！");
                taskService.updateById(task);
                return;
            }
            ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
            HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
            HashMap<String,Object> params = new HashMap<>();
            // 挂起虚拟机硬盘
            params.put("todisk",true);
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
    @Scheduled(fixedDelay = 2000)
    public void resumeVm(){
        QueryWrapper<Task> queryWrap = new QueryWrapper<>();
        queryWrap.eq("type", RESUME_VM);
        queryWrap.eq("status", 0);
        queryWrap.orderByAsc("create_date");
        queryWrap.last("LIMIT 1");
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
            if(vmhost.getStatus() == 6 || vmhost.getStatus() == 13)
            {
                log.error("[Task-ResumeVm] 恢复任务: NodeID:{} VM-ID:{} 失败，创建/重装系统不允许操作！",node.getId(),task.getVmid());
                // 修改任务状态为3 3为执行失败
                task.setStatus(3);
                task.setError("创建/重装系统不允许恢复！");
                taskService.updateById(task);
                return;
            }
            ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
            HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
            HashMap<String,Object> params = new HashMap<>();
            // 先获取虚拟机的状态码
            int vmStatus = masterService.getVmStatusCode(task.getNodeid(), task.getVmid());
            // 如果虚拟机状态为1 关机
            if (vmStatus ==1){
                // 则执行开机操作
                try {
                    proxmoxApiUtil.postNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+task.getVmid()+"/status/start", params);
                } catch (Exception e) {
                    log.error("[Task-ResumeVm] 恢复任务: NodeID:{} VM-ID:{} 失败",node.getId(),task.getVmid());
                    // 修改任务状态为3 3为执行失败
                    task.setStatus(3);
                    task.setError(e.getMessage());
                    taskService.updateById(task);
                    e.printStackTrace();
                    return;
                }
            }
            else {
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
    @Scheduled(fixedDelay = 2000)
    public void pauseVm(){
        QueryWrapper<Task> queryWrap = new QueryWrapper<>();
        // 暂停为挂起操作
        queryWrap.eq("type", PAUSE_VM);
        queryWrap.eq("status", 0);
        queryWrap.orderByAsc("create_date");
        queryWrap.last("LIMIT 1");
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
            if(vmhost.getStatus() == 6 || vmhost.getStatus() == 13)
            {
                log.error("[Task-PauseVm] 暂停任务: NodeID:{} VM-ID:{} 失败，创建/重装系统不允许暂停！",node.getId(),task.getVmid());
                // 修改任务状态为3 3为执行失败
                task.setStatus(3);
                task.setError("创建/重装系统不允许暂停！");
                taskService.updateById(task);
                return;
            }
            // 先获取虚拟机的状态码
            int vmStatus = masterService.getVmStatusCode(task.getNodeid(), task.getVmid());
            // 如果虚拟机状态为1或者 2
            if (vmStatus ==1 || vmStatus == 2){
                // 直接设置数据库中的vm状态为4 4为暂停
                // 设置数据库中的vm状态为4 4为暂停
                vmhost.setStatus(4);
                vmhostService.updateById(vmhost);
                // 设置任务状态为2 2为执行完成
                task.setStatus(2);
                taskService.updateById(task);
                log.info("[Task-PauseVm] 暂停任务: NodeID:{} VM-ID:{} 完成",node.getId(),task.getVmid());
                return;
            }
            ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
            HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
            HashMap<String,Object> params = new HashMap<>();
            // 挂起虚拟机硬盘
            params.put("todisk",true);
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
     * @Description: 超流暂停虚拟机任务
     * @DateTime: 2023/7/18 23:20
     */
    @Async
    @Scheduled(fixedDelay = 2000)
    public void qosPauseVm(){
        QueryWrapper<Task> queryWrap = new QueryWrapper<>();
        // 暂停为挂起操作
        queryWrap.eq("type", QOS_PAUSE);
        queryWrap.eq("status", 0);
        queryWrap.orderByAsc("create_date");
        queryWrap.last("LIMIT 1");
        Task task = taskService.getOne(queryWrap);
        if (task != null){
            // 设置任务状态为1 1为正在执行
            task.setStatus(1);
            taskService.updateById(task);
            // 获取node信息
            Master node = masterService.getById(task.getNodeid());
            log.info("[Task-PauseVm] 执行超流暂停任务: NodeID:{} VM-ID:{}",node.getId(),task.getVmid());
            // 获取vm信息
            Vmhost vmhost = vmhostService.getById(task.getHostid());
            // 先获取虚拟机的状态码
            int vmStatus = masterService.getVmStatusCode(task.getNodeid(), task.getVmid());
            // 如果虚拟机状态为1或者 2
            if (vmStatus ==1 || vmStatus == 2 || vmStatus == 15){
                // 设置任务状态为2 2为执行完成
                task.setStatus(2);
                taskService.updateById(task);
                log.info("[Task-PauseVm] 超流暂停任务: NodeID:{} VM-ID:{} 完成",node.getId(),task.getVmid());
                return;
            }
            ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
            HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
            HashMap<String,Object> params = new HashMap<>();
            // 挂起虚拟机硬盘
            params.put("todisk",true);
            try {
                proxmoxApiUtil.postNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+task.getVmid()+"/status/suspend", params);
            } catch (Exception e) {
                log.error("[Task-PauseVm] 超流暂停任务: NodeID:{} VM-ID:{} 失败",node.getId(),task.getVmid());
                // 修改任务状态为3 3为执行失败
                task.setStatus(3);
                task.setError(e.getMessage());
                taskService.updateById(task);
                e.printStackTrace();
                return;
            }
            // 设置数据库中的vm状态为4 4为暂停
            vmhost.setStatus(15);
            vmhostService.updateById(vmhost);
            // 设置任务状态为2 2为执行完成
            task.setStatus(2);
            taskService.updateById(task);
            log.info("[Task-PauseVm] 超流暂停任务: NodeID:{} VM-ID:{} 完成",node.getId(),task.getVmid());
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 恢复暂停任务
    * @DateTime: 2023/7/18 23:24
    */
    @Async
    @Scheduled(fixedDelay = 2000)
    public void unpauseVm(){
        QueryWrapper<Task> queryWrap = new QueryWrapper<>();
        queryWrap.eq("type", UNPAUSE_VM);
        queryWrap.eq("status", 0);
        queryWrap.orderByAsc("create_date");
        queryWrap.last("LIMIT 1");
        Task task = taskService.getOne(queryWrap);
        if (task != null){
            // 设置任务状态为1 1为正在执行
            task.setStatus(1);
            taskService.updateById(task);
            // 获取node信息
            Master node = masterService.getById(task.getNodeid());
            // 获取vm信息
            Vmhost vmhost = vmhostService.getById(task.getHostid());
            log.info("[Task-UnpauseVm] 执行恢复暂停任务: NodeID:{} VM-ID:{}",node.getId(),task.getVmid());
            // 先获取虚拟机的状态码
            int vmStatus = masterService.getVmStatusCode(task.getNodeid(), task.getVmid());
            ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
            HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
            HashMap<String,Object> params = new HashMap<>();
            // 如果虚拟机状态为1 关机
            if (vmStatus ==1){
                // 则执行开机操作
                try {
                    proxmoxApiUtil.postNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+task.getVmid()+"/status/start", params);
                } catch (Exception e) {
                    log.error("[Task-UnpauseVm] 恢复暂停任务: NodeID:{} VM-ID:{} 失败",node.getId(),task.getVmid());
                    // 修改任务状态为3 3为执行失败
                    task.setStatus(3);
                    task.setError(e.getMessage());
                    taskService.updateById(task);
                    e.printStackTrace();
                    return;
                }
            }
            else {
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
            }
            // 设置数据库中的vm状态为3 3为恢复中
            vmhost.setStatus(3);
            vmhostService.updateById(vmhost);
            // 设置任务状态为2 2为执行完成
            task.setStatus(2);
            taskService.updateById(task);
            log.info("[Task-UnpauseVm] 恢复暂停任务: NodeID:{} VM-ID:{} 完成",node.getId(),task.getVmid());
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 监听所有虚拟机状态
    * @DateTime: 2023/7/19 17:47
    */
    @Async
    @Scheduled(fixedDelay = 1000*5)
    public void listenVmStatus() {
        int i = 1;
        while (true){
            QueryWrapper<Master> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("status",0);
            // 分页获取100行节点实例
            Page<Master> page = masterService.getMasterList(i,100,queryWrapper);
            List<Master> nodes = page.getRecords();
            // 如果为空则跳出循环
            if (nodes.size() == 0){
                break;
            }
            for (Master master: nodes){

                int nodeId = master.getId();
                //  判断节点是否在线
                if (!masterService.isNodeOnline(nodeId)){
                    continue;
                }
                JSONObject vmJson = masterService.getNodeVmInfoJsonList(nodeId);
                // 判空
                if (vmJson == null){
                    continue;
                }
                JSONArray vmList = vmJson.getJSONArray("data");
                // 执行同步更新
                vmhostService.syncVmStatus(vmList,nodeId);
            }
            // 如果当前页数等于总页数则跳出循环
            if (i == page.getPages()){
                break;
            }
            i++;
        }

    }

    /**
     * @Author: mryunqi
     * @Description: 到期监听
     * @DateTime: 2023/9/26 20:45
     */
    @Async
    @Scheduled(fixedDelay = 2000)
    public void expireCron(){
        int i = 1; // 页数
        while (true){
            QueryWrapper<Vmhost> queryWrap = new QueryWrapper<>();
            // 筛选状态不为6
            queryWrap.ne("status", 5);
            // 筛选expirationTime小于等于当前时间
            queryWrap.le("expiration_time", System.currentTimeMillis());
            // 分页获取10行节点实例
            Page<Vmhost> page = vmhostService.selectPage(i,10,queryWrap);
            List<Vmhost> vmList = page.getRecords();
            // 如果为空则跳出循环
            if (vmList.size() == 0){
                break;
            }
            for (Vmhost vmhost : vmList){
                // 判断是否正在运行
                if (vmhost.getStatus() == 0){
                    // 如果正在运行则创建关机任务
                    Task vmStartTask = new Task();
                    vmStartTask.setNodeid(vmhost.getNodeid());
                    vmStartTask.setVmid(vmhost.getVmid());
                    vmStartTask.setHostid(vmhost.getId());
                    vmStartTask.setType(START_VM);
                    vmStartTask.setStatus(0);
                    vmStartTask.setCreateDate(System.currentTimeMillis());
                    taskService.save(vmStartTask);
                    // 等待该任务执行完成
                    int count = 0;
                    while (true){
                        // 如果超过60秒还没有完成，则跳出循环
                        if (count >= 600) {
                            vmhost.setStatus(5);
                            vmhostService.updateById(vmhost);
                            break;
                        }
                        // 休眠1秒
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Task task = taskService.getById(vmStartTask.getId());
                        if (task == null){
                            vmhost.setStatus(5);
                            vmhostService.updateById(vmhost);
                            break;
                        }
                        if (task.getStatus() == 2){
                            vmhost.setStatus(5);
                            vmhostService.updateById(vmhost);
                            break;
                        }
                        if (task.getStatus() == 3){
                            vmhost.setStatus(5);
                            vmhostService.updateById(vmhost);
                            break;
                        }
                        count++;
                    }
                }
                // 如果不是正在运行则直接修改状态为5
                else {
                    vmhost.setStatus(5);
                    vmhostService.updateById(vmhost);
                }
            }
        }

    }

}
