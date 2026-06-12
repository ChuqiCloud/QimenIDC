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
import com.chuqiyun.proxmoxveams.utils.CloudInitNetworkUtil;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import com.chuqiyun.proxmoxveams.utils.SshUtil;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.chuqiyun.proxmoxveams.constant.TaskType.*;

/**
 * @author mryunqi
 * @date 2023/7/1
 */
@Slf4j
@Component
@EnableScheduling
public class VmStatusCron {
    private static final long IP_CHANGE_RESTART_TIMEOUT = 3 * 60 * 1000L;
    private static final long IP_CHANGE_RESTART_WAIT = 2000L;

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
            createApplyWindowsVmIpTaskIfNeeded(vmhost);
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
            // 设置数据库中的vm状态为1 1为关机 非重装任务13和暂停(4)才执行
            if (vmhost.getStatus() != 13 && vmhost.getStatus() != 4) {
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
    * @Author: 星禾
    * @Description: IP变更后异步强制停止并重新开机
    * @DateTime: 2026/6/6 22:10
    */
    @Async
    @Scheduled(fixedDelay = 2000)
    public void ipChangeRestartVm(){
        QueryWrapper<Task> queryWrap = new QueryWrapper<>();
        queryWrap.eq("type", IP_CHANGE_RESTART_VM);
        queryWrap.eq("status", 0);
        queryWrap.orderByAsc("create_date");
        queryWrap.last("LIMIT 1");
        Task task = taskService.getOne(queryWrap);
        if (task == null){
            return;
        }
        task.setStatus(1);
        taskService.updateById(task);
        Master node = masterService.getById(task.getNodeid());
        Vmhost vmhost = vmhostService.getById(task.getHostid());
        if (node == null || vmhost == null){
            task.setStatus(3);
            task.setError("节点或虚拟机不存在");
            taskService.updateById(task);
            log.error("[Task-IpChangeRestart] 执行IP变更重启任务失败: TaskId={}, NodeID={}, VM-ID={}, HostId={}",
                    task.getId(), task.getNodeid(), task.getVmid(), task.getHostid());
            return;
        }
        log.info("[Task-IpChangeRestart] 执行IP变更重启任务: TaskId={}, NodeID={}, VM-ID={}, HostId={}",
                task.getId(), node.getId(), task.getVmid(), task.getHostid());
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
        try {
            String status = getVmStatus(proxmoxApiUtil, node, authentications, task.getVmid());
            if (!"stopped".equals(status)) {
                updateVmStatusOnly(vmhost, 9);
                proxmoxApiUtil.forceStopVm(node, authentications, task.getVmid());
                waitVmStopped(proxmoxApiUtil, node, authentications, task.getVmid());
                updateVmStatusOnly(vmhost, 1);
            }
            updateVmStatusOnly(vmhost, 7);
            proxmoxApiUtil.postNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+task.getVmid()+"/status/start", new HashMap<>());
            updateVmStatusOnly(vmhost, 0);
            createApplyWindowsVmIpTaskIfNeeded(vmhost);
            task.setStatus(2);
            task.setError(null);
            taskService.updateById(task);
            log.info("[Task-IpChangeRestart] IP变更重启任务完成: TaskId={}, NodeID={}, VM-ID={}, HostId={}",
                    task.getId(), node.getId(), task.getVmid(), task.getHostid());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            task.setStatus(3);
            task.setError("等待虚拟机停止被中断");
            taskService.updateById(task);
            log.error("[Task-IpChangeRestart] IP变更重启任务被中断: TaskId={}, NodeID={}, VM-ID={}, HostId={}",
                    task.getId(), node.getId(), task.getVmid(), task.getHostid(), e);
        } catch (Exception e) {
            task.setStatus(3);
            task.setError(e.getMessage());
            taskService.updateById(task);
            log.error("[Task-IpChangeRestart] IP变更重启任务失败: TaskId={}, NodeID={}, VM-ID={}, HostId={}",
                    task.getId(), node.getId(), task.getVmid(), task.getHostid(), e);
        }
    }

    @Async
    @Scheduled(fixedDelay = 5000)
    public void applyWindowsVmIp(){
        QueryWrapper<Task> queryWrap = new QueryWrapper<>();
        queryWrap.eq("type", APPLY_WINDOWS_VM_IP);
        queryWrap.eq("status", 0);
        queryWrap.orderByAsc("create_date");
        queryWrap.last("LIMIT 1");
        Task task = taskService.getOne(queryWrap);
        if (task == null){
            return;
        }
        task.setStatus(1);
        taskService.updateById(task);

        Master node = masterService.getById(task.getNodeid());
        Vmhost vmhost = vmhostService.getById(task.getHostid());
        if (node == null || vmhost == null) {
            task.setStatus(3);
            task.setError("节点或虚拟机不存在");
            taskService.updateById(task);
            return;
        }
        try {
            if (!isWindowsIpManagedVm(vmhost)) {
                task.setStatus(2);
                task.setError(null);
                taskService.updateById(task);
                return;
            }
            ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
            HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
            if (!"running".equals(getVmStatus(proxmoxApiUtil, node, authentications, task.getVmid()))) {
                task.setStatus(0);
                task.setError("等待Windows虚拟机启动");
                taskService.updateById(task);
                return;
            }
            applyWindowsIpByGuestAgent(node, vmhost);
            task.setStatus(2);
            task.setError(null);
            taskService.updateById(task);
            log.info("[Task-ApplyWindowsVmIp] Windows附加IP应用完成: NodeID:{} VM-ID:{} HostId:{}",
                    node.getId(), task.getVmid(), task.getHostid());
        } catch (Exception e) {
            task.setStatus(0);
            task.setError("等待QEMU Guest Agent应用Windows附加IP: " + e.getMessage());
            taskService.updateById(task);
            log.warn("[Task-ApplyWindowsVmIp] Windows附加IP暂未应用，等待下次重试: TaskId={}, NodeID={}, VM-ID={}, HostId={}, Error={}",
                    task.getId(), task.getNodeid(), task.getVmid(), task.getHostid(), e.getMessage());
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
            //params.put("todisk",true);
            try {
                proxmoxApiUtil.postNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+task.getVmid()+"/status/stop", params);
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
            //params.put("todisk",true);
            try {
                proxmoxApiUtil.postNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu/"+task.getVmid()+"/status/stop", params);
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
            // 执行开机操作
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

    private void updateVmStatusOnly(Vmhost vmhost, Integer status) {
        if (vmhost == null || vmhost.getId() == null || status == null) {
            throw new IllegalStateException("更新虚拟机状态参数无效");
        }
        Vmhost updateVmhost = new Vmhost();
        updateVmhost.setId(vmhost.getId());
        updateVmhost.setStatus(status);
        if (!vmhostService.updateById(updateVmhost)) {
            throw new IllegalStateException("更新虚拟机状态失败: hostId=" + vmhost.getId() + ", status=" + status);
        }
        vmhost.setStatus(status);
    }

    private void createApplyWindowsVmIpTaskIfNeeded(Vmhost vmhost) {
        if (!isWindowsIpManagedVm(vmhost) || getPendingApplyWindowsVmIpTask(vmhost.getId()) != null) {
            return;
        }
        Task task = new Task();
        task.setNodeid(vmhost.getNodeid());
        task.setVmid(vmhost.getVmid());
        task.setHostid(vmhost.getId());
        task.setType(APPLY_WINDOWS_VM_IP);
        task.setStatus(0);
        HashMap<Object, Object> params = new HashMap<>();
        params.put("source", "windows_multi_ip");
        task.setParams(params);
        task.setCreateDate(System.currentTimeMillis());
        if (!taskService.insertTask(task)) {
            throw new IllegalStateException("创建Windows附加IP应用任务失败: hostId=" + vmhost.getId());
        }
        vmhostService.addVmHostTask(vmhost.getId(), task.getId());
    }

    private Task getPendingApplyWindowsVmIpTask(Integer hostId) {
        if (hostId == null) {
            return null;
        }
        QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("hostid", hostId);
        queryWrapper.eq("type", APPLY_WINDOWS_VM_IP);
        queryWrapper.in("status", 0, 1);
        queryWrapper.orderByDesc("create_date");
        queryWrapper.last("limit 1");
        return taskService.getOne(queryWrapper);
    }

    private boolean isWindowsIpManagedVm(Vmhost vmhost) {
        return vmhost != null
                && "windows".equalsIgnoreCase(vmhost.getOsType())
                && CloudInitNetworkUtil.getIpAddressCount(vmhost.getIpConfig()) > 0;
    }

    private void applyWindowsIpByGuestAgent(Master node, Vmhost vmhost) throws Exception {
        if (node == null || node.getSshPort() == null || StringUtils.isBlank(node.getSshUsername())
                || StringUtils.isBlank(node.getSshPassword())) {
            throw new IllegalStateException("节点SSH配置不完整，无法通过qm guest exec应用Windows附加IP");
        }
        String script = buildWindowsMultiIpScript(vmhost);
        String encodedCommand = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        String command = "qm guest exec " + vmhost.getVmid()
                + " -- powershell.exe -NoProfile -ExecutionPolicy Bypass -EncodedCommand "
                + shellQuote(encodedCommand);
        SshUtil sshUtil = new SshUtil(node.getHost(), node.getSshPort(), node.getSshUsername(), node.getSshPassword());
        try {
            sshUtil.connect();
            sshUtil.executeCommand(command);
        } finally {
            sshUtil.disconnect();
        }
    }

    private String buildWindowsMultiIpScript(Vmhost vmhost) {
        Map<String, String> ipAddressMap = CloudInitNetworkUtil.getIpAddressMap(vmhost.getIpConfig());
        List<String> desiredIpItems = new ArrayList<>();
        List<String> desiredPrefixItems = new ArrayList<>();
        String primaryIp = null;
        for (Map.Entry<String, String> entry : ipAddressMap.entrySet()) {
            if (primaryIp == null) {
                primaryIp = entry.getKey();
                continue;
            }
            Integer prefixLength = CloudInitNetworkUtil.getPrefixLength(entry.getValue());
            if (prefixLength == null) {
                continue;
            }
            desiredIpItems.add("'" + escapePowerShellString(entry.getKey()) + "'");
            desiredPrefixItems.add("'" + escapePowerShellString(entry.getKey()) + "'=" + prefixLength);
        }
        if (StringUtils.isBlank(primaryIp)) {
            return "";
        }
        String desiredIps = "@(" + String.join(",", desiredIpItems) + ")";
        String desiredPrefixes = "@{" + String.join(";", desiredPrefixItems) + "}";
        String escapedPrimaryIp = escapePowerShellString(primaryIp);
        return "$ErrorActionPreference = 'Stop'\n"
                + "$primaryIp = '" + escapedPrimaryIp + "'\n"
                + "$desiredIps = " + desiredIps + "\n"
                + "$prefixMap = " + desiredPrefixes + "\n"
                + "$adapter = Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -eq $primaryIp } | Select-Object -First 1\n"
                + "if ($null -eq $adapter) { throw \"Primary IP $primaryIp not found\" }\n"
                + "$index = $adapter.InterfaceIndex\n"
                + "$currentIps = Get-NetIPAddress -AddressFamily IPv4 -InterfaceIndex $index | Where-Object { $_.PrefixOrigin -ne 'WellKnown' }\n"
                + "foreach ($ip in $currentIps) {\n"
                + "    if ($ip.IPAddress -ne $primaryIp -and $desiredIps -notcontains $ip.IPAddress) {\n"
                + "        Remove-NetIPAddress -InterfaceIndex $index -IPAddress $ip.IPAddress -Confirm:$false\n"
                + "    }\n"
                + "}\n"
                + "foreach ($ip in $desiredIps) {\n"
                + "    $exists = Get-NetIPAddress -AddressFamily IPv4 -InterfaceIndex $index -IPAddress $ip -ErrorAction SilentlyContinue\n"
                + "    if ($null -eq $exists) {\n"
                + "        New-NetIPAddress -InterfaceIndex $index -IPAddress $ip -PrefixLength ([int]$prefixMap[$ip]) | Out-Null\n"
                + "    }\n"
                + "}\n";
    }

    private String escapePowerShellString(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private String shellQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private void waitVmStopped(ProxmoxApiUtil proxmoxApiUtil, Master node, HashMap<String, String> authentications, Integer vmid) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime <= IP_CHANGE_RESTART_TIMEOUT) {
            if ("stopped".equals(getVmStatus(proxmoxApiUtil, node, authentications, vmid))) {
                return;
            }
            Thread.sleep(IP_CHANGE_RESTART_WAIT);
        }
        throw new IllegalStateException("等待虚拟机强制停止超时: vmid=" + vmid);
    }

    private String getVmStatus(ProxmoxApiUtil proxmoxApiUtil, Master node, HashMap<String, String> authentications, Integer vmid) {
        JSONObject result = proxmoxApiUtil.getVmStatus(node, authentications, vmid);
        if (result == null || result.getJSONObject("data") == null) {
            throw new IllegalStateException("获取虚拟机状态失败: vmid=" + vmid);
        }
        String status = result.getJSONObject("data").getString("status");
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalStateException("虚拟机状态为空: vmid=" + vmid);
        }
        return status;
    }

}
