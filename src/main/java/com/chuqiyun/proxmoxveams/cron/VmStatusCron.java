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
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static final long APPLY_WINDOWS_VM_IP_RETRY_DELAY = 10_000L;
    private static final int APPLY_WINDOWS_VM_IP_MAX_RETRY = 30;
    private static final long WINDOWS_GUEST_AGENT_COMMAND_TIMEOUT = 120_000L;

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
        queryWrap.le("create_date", System.currentTimeMillis());
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
    @Scheduled(fixedDelay = APPLY_WINDOWS_VM_IP_RETRY_DELAY)
    public void applyWindowsVmIp(){
        QueryWrapper<Task> queryWrap = new QueryWrapper<>();
        queryWrap.eq("type", APPLY_WINDOWS_VM_IP);
        queryWrap.eq("status", 0);
        queryWrap.le("create_date", System.currentTimeMillis());
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
        if (isApplyWindowsVmIpRetryExceeded(task)) {
            task.setStatus(3);
            task.setError("Windows附加IP应用失败，超过最大重试次数，最后错误: " + StringUtils.defaultIfBlank(task.getError(), "未知错误"));
            taskService.updateById(task);
            log.error("[Task-ApplyWindowsVmIp] Windows附加IP应用失败，超过最大重试次数: TaskId={}, NodeID={}, VM-ID={}, HostId={}, Error={}",
                    task.getId(), task.getNodeid(), task.getVmid(), task.getHostid(), task.getError());
            return;
        }
        int retryCount = increaseApplyWindowsVmIpRetryCount(task);
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
                scheduleNextApplyWindowsVmIpRetry(task, retryCount, "等待Windows虚拟机启动");
                return;
            }
            JSONObject pveVmConfig = proxmoxApiUtil.getVmConfig(node, authentications, task.getVmid());
            applyWindowsIpByGuestAgent(proxmoxApiUtil, node, authentications, vmhost, getNameserversFromPveConfig(pveVmConfig));
            task.setStatus(2);
            task.setError(null);
            taskService.updateById(task);
            log.info("[Task-ApplyWindowsVmIp] Windows附加IP应用完成: NodeID:{} VM-ID:{} HostId:{} Retry:{}",
                    node.getId(), task.getVmid(), task.getHostid(), retryCount);
        } catch (Exception e) {
            if (retryCount >= APPLY_WINDOWS_VM_IP_MAX_RETRY) {
                task.setStatus(3);
                task.setError("Windows附加IP应用失败，超过最大重试次数，最后错误: " + StringUtils.defaultIfBlank(e.getMessage(), "未知错误"));
                taskService.updateById(task);
                log.error("[Task-ApplyWindowsVmIp] Windows附加IP应用失败，超过最大重试次数: TaskId={}, NodeID={}, VM-ID={}, HostId={}, Retry={}, Error={}",
                        task.getId(), task.getNodeid(), task.getVmid(), task.getHostid(), retryCount, e.getMessage(), e);
                return;
            }
            scheduleNextApplyWindowsVmIpRetry(task, retryCount, "等待QEMU Guest Agent应用Windows附加IP: " + e.getMessage());
            log.warn("[Task-ApplyWindowsVmIp] Windows附加IP暂未应用，10秒后重试: TaskId={}, NodeID={}, VM-ID={}, HostId={}, Retry={}/{}, Error={}",
                    task.getId(), task.getNodeid(), task.getVmid(), task.getHostid(), retryCount, APPLY_WINDOWS_VM_IP_MAX_RETRY, e.getMessage());
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
        params.put("source", "windows_ip_sync");
        params.put("retryCount", 0);
        params.put("maxRetry", APPLY_WINDOWS_VM_IP_MAX_RETRY);
        params.put("retryDelaySeconds", APPLY_WINDOWS_VM_IP_RETRY_DELAY / 1000);
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
                && CloudInitNetworkUtil.getIpConfigEntryCount(vmhost.getIpConfig()) > 1;
    }

    private boolean isApplyWindowsVmIpRetryExceeded(Task task) {
        return getApplyWindowsVmIpRetryCount(task) >= APPLY_WINDOWS_VM_IP_MAX_RETRY;
    }

    private int increaseApplyWindowsVmIpRetryCount(Task task) {
        Map<Object, Object> params = ensureTaskParams(task);
        int retryCount = getApplyWindowsVmIpRetryCount(task) + 1;
        params.put("retryCount", retryCount);
        params.put("maxRetry", APPLY_WINDOWS_VM_IP_MAX_RETRY);
        params.put("retryDelaySeconds", APPLY_WINDOWS_VM_IP_RETRY_DELAY / 1000);
        task.setParams(params);
        return retryCount;
    }

    private int getApplyWindowsVmIpRetryCount(Task task) {
        if (task == null || task.getParams() == null || task.getParams().get("retryCount") == null) {
            return 0;
        }
        try {
            return Integer.parseInt(task.getParams().get("retryCount").toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void scheduleNextApplyWindowsVmIpRetry(Task task, int retryCount, String error) {
        task.setStatus(0);
        task.setCreateDate(System.currentTimeMillis() + APPLY_WINDOWS_VM_IP_RETRY_DELAY);
        task.setError(error + "，重试进度: " + retryCount + "/" + APPLY_WINDOWS_VM_IP_MAX_RETRY);
        taskService.updateById(task);
    }

    private Map<Object, Object> ensureTaskParams(Task task) {
        if (task.getParams() == null) {
            task.setParams(new HashMap<>());
        }
        return task.getParams();
    }

    private void applyWindowsIpByGuestAgent(ProxmoxApiUtil proxmoxApiUtil, Master node, HashMap<String, String> cookieMap,
                                            Vmhost vmhost, List<String> nameservers) throws Exception {
        String script = buildWindowsIpSyncScript(vmhost, nameservers);
        JSONObject execResult = proxmoxApiUtil.guestExecPowerShell(node, cookieMap, vmhost.getVmid(), script);
        Integer pid = extractGuestExecPid(execResult);
        JSONObject execStatus = waitWindowsGuestExecStatus(proxmoxApiUtil, node, cookieMap, vmhost, pid);
        Integer exitCode = extractGuestExecExitCode(execStatus);
        if (exitCode == null || exitCode != 0) {
            throw new IllegalStateException("Windows IP sync failed, exitCode=" + exitCode
                    + ", stdout=" + StringUtils.defaultString(extractGuestExecOutput(execStatus, "out-data", "out_data", "stdout"))
                    + ", stderr=" + StringUtils.defaultString(extractGuestExecOutput(execStatus, "err-data", "err_data", "stderr")));
        }
    }

    private JSONObject waitWindowsGuestExecStatus(ProxmoxApiUtil proxmoxApiUtil, Master node, HashMap<String, String> cookieMap,
                                                  Vmhost vmhost, Integer pid) throws InterruptedException {
        if (pid == null) {
            throw new IllegalStateException("QEMU Guest Agent did not return process pid");
        }
        long endTime = System.currentTimeMillis() + WINDOWS_GUEST_AGENT_COMMAND_TIMEOUT;
        JSONObject lastStatus = null;
        while (System.currentTimeMillis() <= endTime) {
            lastStatus = proxmoxApiUtil.guestExecStatus(node, cookieMap, vmhost.getVmid(), pid);
            JSONObject data = lastStatus == null ? null : lastStatus.getJSONObject("data");
            if (data != null && data.getBooleanValue("exited")) {
                return data;
            }
            Thread.sleep(2000L);
        }
        throw new IllegalStateException("Windows IP sync command timeout, pid=" + pid
                + ", lastStatus=" + (lastStatus == null ? "" : lastStatus.toJSONString()));
    }

    private Integer extractGuestExecPid(JSONObject execResult) {
        JSONObject data = execResult == null ? null : execResult.getJSONObject("data");
        return data == null ? null : data.getInteger("pid");
    }

    private Integer extractGuestExecExitCode(JSONObject status) {
        if (status == null) {
            return null;
        }
        Integer exitCode = status.getInteger("exitcode");
        return exitCode == null ? status.getInteger("exit-code") : exitCode;
    }

    private String extractGuestExecOutput(JSONObject status, String... keys) {
        if (status == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            String value = status.getString(key);
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String buildWindowsIpSyncScript(Vmhost vmhost, List<String> nameservers) {
        List<WindowsIpConfig> ipConfigs = parseWindowsIpConfigs(vmhost.getIpConfig());
        List<String> desiredIpv4Items = new ArrayList<>();
        List<String> desiredIpv6Items = new ArrayList<>();
        for (WindowsIpConfig item : ipConfigs) {
            String powershellItem = "@{IPAddress='" + escapePowerShellString(item.ip) + "';PrefixLength="
                    + item.prefixLength + ";Gateway='" + escapePowerShellString(item.gateway) + "'}";
            if (item.ipv6) {
                desiredIpv6Items.add(powershellItem);
            } else {
                desiredIpv4Items.add(powershellItem);
            }
        }
        if (desiredIpv4Items.isEmpty() && desiredIpv6Items.isEmpty()) {
            return "";
        }
        return "$ErrorActionPreference = 'Stop'\n"
                + "$desiredIpv4s = @(" + String.join(",", desiredIpv4Items) + ")\n"
                + "$desiredIpv6s = @(" + String.join(",", desiredIpv6Items) + ")\n"
                + "$dnsServers = " + buildPowershellStringArray(nameservers) + "\n"
                + "function Has-Command($name) { $null -ne (Get-Command $name -ErrorAction SilentlyContinue) }\n"
                + "function Prefix-ToMask([int]$prefix) {\n"
                + "    $mask = [uint32]0\n"
                + "    for ($i = 0; $i -lt $prefix; $i++) { $mask = $mask -bor ([uint32]1 -shl (31 - $i)) }\n"
                + "    return ((($mask -shr 24) -band 255), (($mask -shr 16) -band 255), (($mask -shr 8) -band 255), ($mask -band 255)) -join '.'\n"
                + "}\n"
                + "function Get-TargetAdapter {\n"
                + "    try {\n"
                + "        $route = Get-NetRoute -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue | Sort-Object RouteMetric,InterfaceMetric | Select-Object -First 1\n"
                + "        if ($null -eq $route) { $route = Get-NetRoute -DestinationPrefix '::/0' -ErrorAction SilentlyContinue | Sort-Object RouteMetric,InterfaceMetric | Select-Object -First 1 }\n"
                + "        if ($null -ne $route) {\n"
                + "            $adapter = Get-NetAdapter -InterfaceIndex $route.InterfaceIndex -ErrorAction SilentlyContinue\n"
                + "            if ($null -ne $adapter) { return @{Index=$adapter.ifIndex;Alias=$adapter.Name} }\n"
                + "        }\n"
                + "    } catch {}\n"
                + "    try {\n"
                + "        $adapter = Get-NetAdapter -ErrorAction SilentlyContinue | Where-Object { $_.Status -eq 'Up' -and $_.HardwareInterface -ne $false } | Sort-Object ifIndex | Select-Object -First 1\n"
                + "        if ($null -ne $adapter) { return @{Index=$adapter.ifIndex;Alias=$adapter.Name} }\n"
                + "    } catch {}\n"
                + "    try {\n"
                + "        $adapter = Get-WmiObject Win32_NetworkAdapter -Filter \"NetEnabled=true\" | Where-Object { $_.NetConnectionID } | Sort-Object InterfaceIndex | Select-Object -First 1\n"
                + "        if ($null -ne $adapter) { return @{Index=$adapter.InterfaceIndex;Alias=$adapter.NetConnectionID} }\n"
                + "    } catch {}\n"
                + "    throw 'No enabled network adapter found'\n"
                + "}\n"
                + "function Ensure-DefaultRoute($family, $gateway) {\n"
                + "    if ([string]::IsNullOrWhiteSpace($gateway)) { return }\n"
                + "    $dest = if ($family -eq 'IPv6') { '::/0' } else { '0.0.0.0/0' }\n"
                + "    if (Has-Command 'Get-NetRoute') {\n"
                + "        $exists = Get-NetRoute -DestinationPrefix $dest -InterfaceIndex $index -NextHop $gateway -ErrorAction SilentlyContinue\n"
                + "        if ($null -eq $exists) { New-NetRoute -DestinationPrefix $dest -InterfaceIndex $index -NextHop $gateway -ErrorAction SilentlyContinue | Out-Null }\n"
                + "        return\n"
                + "    }\n"
                + "    if ($family -eq 'IPv6') { netsh interface ipv6 add route ::/0 \"$alias\" $gateway publish=no | Out-Null }\n"
                + "    else { netsh interface ipv4 add route 0.0.0.0/0 \"$alias\" $gateway | Out-Null }\n"
                + "}\n"
                + "function Ensure-Ipv4($item, [bool]$primary) {\n"
                + "    if (Has-Command 'Get-NetIPAddress') {\n"
                + "        $exists = Get-NetIPAddress -AddressFamily IPv4 -InterfaceIndex $index -IPAddress $item.IPAddress -ErrorAction SilentlyContinue\n"
                + "        if ($null -eq $exists) { New-NetIPAddress -AddressFamily IPv4 -InterfaceIndex $index -IPAddress $item.IPAddress -PrefixLength ([int]$item.PrefixLength) -ErrorAction Stop | Out-Null }\n"
                + "        if ($primary) { Ensure-DefaultRoute 'IPv4' $item.Gateway }\n"
                + "        return\n"
                + "    }\n"
                + "    $mask = Prefix-ToMask ([int]$item.PrefixLength)\n"
                + "    if ($primary -and -not [string]::IsNullOrWhiteSpace($item.Gateway)) { netsh interface ipv4 set address name=\"$alias\" static $item.IPAddress $mask $item.Gateway | Out-Null }\n"
                + "    else { netsh interface ipv4 add address name=\"$alias\" addr=$item.IPAddress mask=$mask | Out-Null }\n"
                + "}\n"
                + "function Ensure-Ipv6($item, [bool]$primary) {\n"
                + "    if (Has-Command 'Get-NetIPAddress') {\n"
                + "        $exists = Get-NetIPAddress -AddressFamily IPv6 -InterfaceIndex $index -IPAddress $item.IPAddress -ErrorAction SilentlyContinue\n"
                + "        if ($null -eq $exists) { New-NetIPAddress -AddressFamily IPv6 -InterfaceIndex $index -IPAddress $item.IPAddress -PrefixLength ([int]$item.PrefixLength) -ErrorAction Stop | Out-Null }\n"
                + "        if ($primary) { Ensure-DefaultRoute 'IPv6' $item.Gateway }\n"
                + "        return\n"
                + "    }\n"
                + "    netsh interface ipv6 add address \"$alias\" ($item.IPAddress + '/' + $item.PrefixLength) | Out-Null\n"
                + "    if ($primary) { Ensure-DefaultRoute 'IPv6' $item.Gateway }\n"
                + "}\n"
                + "$target = Get-TargetAdapter\n"
                + "$index = [int]$target.Index\n"
                + "$alias = [string]$target.Alias\n"
                + "if ([string]::IsNullOrWhiteSpace($alias)) { throw 'Network adapter alias is empty' }\n"
                + "try { Enable-NetAdapterBinding -Name $alias -ComponentID ms_tcpip6 -ErrorAction SilentlyContinue | Out-Null } catch {}\n"
                + "try { netsh interface ipv6 set interface \"$alias\" admin=enabled | Out-Null } catch {}\n"
                + "$primary = $true\n"
                + "foreach ($item in $desiredIpv4s) { Ensure-Ipv4 $item $primary; $primary = $false }\n"
                + "$primary = $true\n"
                + "foreach ($item in $desiredIpv6s) { Ensure-Ipv6 $item $primary; $primary = $false }\n"
                + "if ($dnsServers.Count -gt 0) {\n"
                + "    if (Has-Command 'Set-DnsClientServerAddress') { Set-DnsClientServerAddress -InterfaceIndex $index -ServerAddresses $dnsServers -ErrorAction SilentlyContinue }\n"
                + "    else {\n"
                + "        $v4dns = @($dnsServers | Where-Object { $_ -notlike '*:*' })\n"
                + "        $v6dns = @($dnsServers | Where-Object { $_ -like '*:*' })\n"
                + "        for ($i = 0; $i -lt $v4dns.Count; $i++) { if ($i -eq 0) { netsh interface ipv4 set dnsservers name=\"$alias\" static $v4dns[$i] primary | Out-Null } else { netsh interface ipv4 add dnsservers name=\"$alias\" $v4dns[$i] index=($i + 1) | Out-Null } }\n"
                + "        for ($i = 0; $i -lt $v6dns.Count; $i++) { if ($i -eq 0) { netsh interface ipv6 set dnsservers \"$alias\" static $v6dns[$i] primary | Out-Null } else { netsh interface ipv6 add dnsservers \"$alias\" $v6dns[$i] index=($i + 1) | Out-Null } }\n"
                + "    }\n"
                + "}\n";
    }

    private List<WindowsIpConfig> parseWindowsIpConfigs(Map<String, String> ipConfig) {
        if (ipConfig == null || ipConfig.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> keys = new ArrayList<>(ipConfig.keySet());
        keys.sort(Comparator.comparingInt(this::getIpConfigIndex));
        List<WindowsIpConfig> result = new ArrayList<>();
        for (String key : keys) {
            String config = ipConfig.get(key);
            if (StringUtils.isBlank(config)) {
                continue;
            }
            String address4 = null;
            String gateway4 = null;
            String address6 = null;
            String gateway6 = null;
            for (String token : config.split(",")) {
                String item = token == null ? null : token.trim();
                if (StringUtils.isBlank(item)) {
                    continue;
                }
                if (item.startsWith("ip=")) {
                    address4 = item.substring(3);
                } else if (item.startsWith("gw=")) {
                    gateway4 = item.substring(3);
                } else if (item.startsWith("ip6=")) {
                    address6 = item.substring(4);
                } else if (item.startsWith("gw6=")) {
                    gateway6 = item.substring(4);
                }
            }
            addWindowsIpConfig(result, address4, gateway4, false);
            addWindowsIpConfig(result, address6, gateway6, true);
        }
        return result;
    }

    private void addWindowsIpConfig(List<WindowsIpConfig> result, String address, String gateway, boolean ipv6) {
        if (StringUtils.isBlank(address) || "dhcp".equalsIgnoreCase(address)) {
            return;
        }
        Integer prefixLength = CloudInitNetworkUtil.getPrefixLength(address);
        String ip = getIpWithoutPrefix(address);
        if (prefixLength == null || StringUtils.isBlank(ip)) {
            return;
        }
        result.add(new WindowsIpConfig(ip, prefixLength, StringUtils.defaultString(gateway), ipv6));
    }

    private String getIpWithoutPrefix(String address) {
        if (StringUtils.isBlank(address)) {
            return null;
        }
        int index = address.indexOf('/');
        return index > 0 ? address.substring(0, index) : address;
    }

    private int getIpConfigIndex(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private List<String> getNameserversFromPveConfig(JSONObject pveVmConfig) {
        if (pveVmConfig == null || StringUtils.isBlank(pveVmConfig.getString("nameserver"))) {
            return Collections.emptyList();
        }
        Set<String> nameservers = new LinkedHashSet<>();
        for (String item : pveVmConfig.getString("nameserver").split("[,\\s]+")) {
            if (StringUtils.isNotBlank(item)) {
                nameservers.add(item.trim());
            }
        }
        return new ArrayList<>(nameservers);
    }

    private String buildPowershellStringArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "@()";
        }
        List<String> items = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                items.add("'" + escapePowerShellString(value.trim()) + "'");
            }
        }
        return "@(" + String.join(",", items) + ")";
    }

    private static class WindowsIpConfig {
        private final String ip;
        private final Integer prefixLength;
        private final String gateway;
        private final boolean ipv6;

        private WindowsIpConfig(String ip, Integer prefixLength, String gateway, boolean ipv6) {
            this.ip = ip;
            this.prefixLength = prefixLength;
            this.gateway = gateway;
            this.ipv6 = ipv6;
        }
    }

    private String escapePowerShellString(String value) {
        return value == null ? "" : value.replace("'", "''");
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
