package com.chuqiyun.proxmoxveams.cron;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.IppoolService;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.NatForwardSyncService;
import com.chuqiyun.proxmoxveams.service.SecurityGroupBusinessService;
import com.chuqiyun.proxmoxveams.service.SubnetpoolService;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import javax.annotation.Resource;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.chuqiyun.proxmoxveams.constant.TaskType.*;

/**
 * @author mryunqi
 * @date 2023/9/2
 */
@Slf4j
@Component
@EnableScheduling
public class DeleteVmCron {
    private static final String NETWORK_TYPE_VPC = "vpc";
    private static final int NAT_RULE_CLEANUP_PAGE_SIZE = 500;

    @Resource
    private MasterService masterService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private TaskService taskService;
    @Resource
    private IppoolService ippoolService;
    @Resource
    private SubnetpoolService subnetpoolService;
    @Resource
    private SecurityGroupBusinessService securityGroupBusinessService;
    @Resource
    private NatForwardSyncService natForwardSyncService;
    @Resource
    private ConfigService configService;
    private final AtomicBoolean deleteVmRunning = new AtomicBoolean(false);
    private final AtomicBoolean deleteRecycleRunning = new AtomicBoolean(false);

    /**
    * @Author: mryunqi
    * @Description: 删除虚拟机
    * @DateTime: 2023/9/2 16:07
    */
    private void deleteVpcIpForwards(Vmhost vmhost) {
        if (!isVpcNetwork(vmhost)) {
            return;
        }
        Object vmVpcForward = vmhostService.getVmhostVpcIpForward(vmhost.getId());
        if (!(vmVpcForward instanceof ResponseResult<?>)) {
            throw new IllegalStateException("读取虚拟机 VPC 转发列表失败，停止删除以保留 IP 分配");
        }
        ResponseResult<?> response = (ResponseResult<?>) vmVpcForward;
        if (!Objects.equals(response.getCode(), ResponseResult.RespCode.OK.getCode())) {
            throw new IllegalStateException("读取虚拟机 VPC 转发列表失败，停止删除以保留 IP 分配");
        }
        Object data = response.getData();
        if (!(data instanceof List<?>)) {
            throw new IllegalStateException("虚拟机 VPC 转发列表格式无效，停止删除以保留 IP 分配");
        }

        List<String[]> forwards = new ArrayList<>();
        for (Object item : (List<?>) data) {
            if (!(item instanceof Map<?, ?>)) {
                throw new IllegalStateException("虚拟机 VPC 转发列表包含无效条目，停止删除以保留 IP 分配");
            }
            Map<?, ?> forward = (Map<?, ?>) item;
            Object publicIp = forward.get("publicIp");
            Object privateIp = forward.get("privateIp");
            if (!(publicIp instanceof String) || ((String) publicIp).trim().isEmpty()
                    || !(privateIp instanceof String) || ((String) privateIp).trim().isEmpty()) {
                throw new IllegalStateException("虚拟机 VPC 转发列表缺少有效 IP，停止删除以保留 IP 分配");
            }
            forwards.add(new String[]{((String) publicIp).trim(), ((String) privateIp).trim()});
        }

        for (String[] forward : forwards) {
            if (!Boolean.TRUE.equals(vmhostService.delVmhostVpcIpForward(vmhost.getId(), forward[0], forward[1]))) {
                throw new IllegalStateException("删除虚拟机 VPC 转发失败，停止删除以保留 IP 分配: " + forward[0]);
            }
        }
    }

    private void deletePortNatForwards(Task task, Master node) {
        List<JSONObject> rules = new ArrayList<>();
        for (int page = 1; ; page++) {
            JSONArray pageRules = natForwardSyncService.getPortRulesByHost(
                    task.getHostid(), page, NAT_RULE_CLEANUP_PAGE_SIZE);
            if (pageRules == null) {
                throw new IllegalStateException("读取虚拟机 NAT 转发列表失败，停止删除");
            }
            for (int index = 0; index < pageRules.size(); index++) {
                JSONObject rule = pageRules.getJSONObject(index);
                validatePortNatRule(rule, task.getHostid());
                rules.add(rule);
            }
            if (pageRules.size() < NAT_RULE_CLEANUP_PAGE_SIZE) {
                break;
            }
        }

        for (JSONObject rule : rules) {
            Integer sourcePort = rule.getInteger("source_port");
            String sourceIp = rule.getString("source_ip");
            String protocol = rule.getString("protocol");
            if (!deletePortForwardOnNode(node, configService.getToken(), rule.getInteger("vm"),
                    sourceIp, sourcePort, rule.getString("destination_ip"),
                    rule.getInteger("destination_port"), protocol)) {
                throw new IllegalStateException("宿主机拒绝删除 NAT 转发，停止删除以保留 IP 分配: " + sourceIp + ":" + sourcePort);
            }
            if (!natForwardSyncService.deletePortRule(node.getId(), sourceIp, sourcePort, protocol)) {
                throw new IllegalStateException("删除本地 NAT 转发记录失败，停止删除以保留 IP 分配: " + sourceIp + ":" + sourcePort);
            }
        }
    }

    private void validatePortNatRule(JSONObject rule, Integer hostId) {
        String protocol = rule == null ? null : rule.getString("protocol");
        if (rule == null
                || !Objects.equals(rule.getInteger("vm"), hostId)
                || isBlank(rule.getString("source_ip"))
                || isBlank(rule.getString("destination_ip"))
                || !isValidPort(rule.getInteger("source_port"))
                || !isValidPort(rule.getInteger("destination_port"))
                || !("tcp".equalsIgnoreCase(protocol) || "udp".equalsIgnoreCase(protocol))) {
            throw new IllegalStateException("虚拟机 NAT 转发列表包含无效条目，停止删除");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isValidPort(Integer port) {
        return port != null && port >= 1 && port <= 65535;
    }

    protected ProxmoxApiUtil createProxmoxApiUtil() {
        return new ProxmoxApiUtil();
    }

    protected boolean deletePortForwardOnNode(Master node, String token, Integer vm, String sourceIp,
                                              Integer sourcePort, String destinationIp, Integer destinationPort,
                                              String protocol) {
        return Boolean.TRUE.equals(ClientApiUtil.deletePortForward(node.getHost(), token, node.getControllerPort(),
                vm, sourceIp, sourcePort, destinationIp, destinationPort, protocol));
    }

    private void releaseSubnetpoolByVmhost(Vmhost vmhost) {
        if (!isVpcNetwork(vmhost) || vmhost.getNodeid() == null || vmhost.getVmid() == null) {
            return;
        }
        QueryWrapper<com.chuqiyun.proxmoxveams.entity.Subnetpool> assignedQuery = new QueryWrapper<>();
        assignedQuery.eq("node_id", vmhost.getNodeid());
        assignedQuery.eq("vm_id", vmhost.getVmid());
        if (subnetpoolService.count(assignedQuery) == 0) {
            return;
        }

        UpdateWrapper<com.chuqiyun.proxmoxveams.entity.Subnetpool> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("node_id", vmhost.getNodeid());
        updateWrapper.eq("vm_id", vmhost.getVmid());
        updateWrapper.set("status", 0);
        updateWrapper.set("vm_id", 0);
        subnetpoolService.update(updateWrapper);
        if (subnetpoolService.count(assignedQuery) > 0) {
            throw new IllegalStateException("释放虚拟机子网地址失败，停止释放公网 IP");
        }
    }

    private void deleteSecurityGroups(Vmhost vmhost) {
        try {
            if (vmhost == null || vmhost.getId() == null) {
                return;
            }
            Boolean result = securityGroupBusinessService.deleteVmSecurityGroups(vmhost.getId());
            if (!Boolean.TRUE.equals(result)) {
                log.warn("[DeleteVmCron] 删除安全组失败: HostId={}, VmId={}", vmhost.getId(), vmhost.getVmid());
            }
        } catch (Exception e) {
            log.warn("[DeleteVmCron] 删除安全组异常: HostId={}, VmId={}, Error={}",
                    vmhost == null ? null : vmhost.getId(),
                    vmhost == null ? null : vmhost.getVmid(),
                    e.getMessage());
        }
    }

    private boolean isVpcNetwork(Vmhost vmhost) {
        return vmhost != null && NETWORK_TYPE_VPC.equalsIgnoreCase(vmhost.getNetworkType());
    }

    @Async("deleteVmExecutor")
    @Scheduled(fixedDelay = 2000)
    public void deleteVm() {
        if (!deleteVmRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            processDeleteVm();
        } finally {
            deleteVmRunning.set(false);
        }
    }

    private void processDeleteVm() {
        QueryWrapper<Task> taskQueryWrapper = new QueryWrapper<>();
        taskQueryWrapper.eq("type", DELETE_VM);
        taskQueryWrapper.eq("status", 0);
        taskQueryWrapper.orderByAsc("create_date");
        Page<Task> taskPage = taskService.getTaskList(1, 1, taskQueryWrapper);
        // 判断是否没有任务
        if (taskPage.getRecords().size() == 0) {
            return;
        }
        Task task = taskPage.getRecords().get(0);
        UpdateWrapper<Task> claimWrapper = new UpdateWrapper<>();
        claimWrapper.eq("id", task.getId()).eq("status", 0).set("status", 1);
        if (!taskService.update(claimWrapper)) {
            return;
        }
        task.setStatus(1);
        try {
            // 获取虚拟机配置信息
            Vmhost vmhost = vmhostService.getById(task.getHostid());
            if (vmhost == null) {
                task.setStatus(2);
                task.setError("虚拟机记录不存在，删除任务已结束");
                taskService.updateById(task);
                log.info("[DeleteVmCron] 虚拟机记录不存在，结束删除任务: TaskId={}, HostId={}, VmId={}",
                        task.getId(), task.getHostid(), task.getVmid());
                return;
            }
            // 获取node信息
            Master node = masterService.getById(task.getNodeid());
            if (node == null) {
                task.setStatus(2);
                task.setError("宿主机节点不存在，删除任务已结束");
                taskService.updateById(task);
                log.warn("[DeleteVmCron] 宿主机节点不存在，结束删除任务: TaskId={}, NodeId={}, HostId={}, VmId={}",
                        task.getId(), task.getNodeid(), task.getHostid(), task.getVmid());
                return;
            }
            HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
            ProxmoxApiUtil proxmoxApiUtil = createProxmoxApiUtil();
            JSONObject vmInfo;
            // 获取虚拟机实时信息
            try {
                vmInfo = proxmoxApiUtil.getVmStatus(node, authentications, vmhost.getVmid());
            } catch (Exception e) {
                log.warn("[DeleteVmCron] 获取PVE虚拟机状态失败，直接尝试执行PVE删除: TaskId={}, NodeID={}, HostId={}, VmId={}, Error={}",
                        task.getId(), task.getNodeid(), task.getHostid(), task.getVmid(), e.getMessage());
                vmInfo = null;
            }
            // 如果vmInfo不为空，才进行下一步操作
            if (vmInfo != null) {
                JSONObject vmStatusData = vmInfo.getJSONObject("data");
                String pveStatus = vmStatusData == null ? vmInfo.getString("status") : vmStatusData.getString("status");
                if (isPveStopped(pveStatus)) {
                    log.info("[DeleteVmCron] PVE确认虚拟机已关机，继续删除: TaskId={}, HostId={}, VmId={}",
                            task.getId(), task.getHostid(), task.getVmid());
                } else if ("running".equalsIgnoreCase(pveStatus)) {
                    log.info("[DeleteVmCron] PVE确认虚拟机运行中，创建或复用强制停止任务: TaskId={}, HostId={}, VmId={}",
                            task.getId(), task.getHostid(), task.getVmid());
                    ensureForceStopTask(vmhost);
                    if (vmhost.getStatus() != 9) {
                        vmhost.setStatus(9);
                        vmhostService.updateById(vmhost);
                    }
                    task.setStatus(0);
                    taskService.updateById(task);
                    return;
                } else {
                    log.info("[DeleteVmCron] PVE虚拟机处于过渡状态{}，等待下一轮确认，不创建强制停止任务: TaskId={}, HostId={}, VmId={}",
                            pveStatus, task.getId(), task.getHostid(), task.getVmid());
                    task.setStatus(0);
                    taskService.updateById(task);
                    return;
                }
            }
            deletePortNatForwards(task, node);
            deleteVpcIpForwards(vmhost);
            if (vmInfo != null) {
                task.setStatus(1);
                taskService.updateById(task);
            }
            deletePveVmOrIgnoreMissing(proxmoxApiUtil, node, authentications, task);
            finishDeletedVm(task, vmhost);
        } catch (Exception e) {
            task.setStatus(3);
            task.setError(e.getMessage());
            taskService.updateById(task);
            log.error("[DeleteVmCron] 删除任务执行失败: TaskId={}, NodeID={}, HostId={}, VmId={}",
                    task.getId(), task.getNodeid(), task.getHostid(), task.getVmid(), e);
        }
    }
    /**
     * @Author: 星禾
     * @Description: 15分钟运行一次 删除回收站虚拟机
     * @DateTime: 2026/5/23 23:25
     */
    @Async("deleteRecycleExecutor")
    @Scheduled(fixedDelay = 15 * 60 * 1000)
    public void deleteRecycleVm() {
        if (!deleteRecycleRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            processRecycleVm();
        } finally {
            deleteRecycleRunning.set(false);
        }
    }

    private boolean isPveStopped(String pveStatus) {
        return "stopped".equalsIgnoreCase(pveStatus);
    }

    private boolean isVmConfigMissing(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("Configuration file")
                    && message.contains("does not exist"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void deletePveVmOrIgnoreMissing(ProxmoxApiUtil proxmoxApiUtil, Master node,
                                            HashMap<String, String> authentications, Task task) throws Exception {
        try {
            proxmoxApiUtil.deleteVm(node, authentications, task.getVmid());
        } catch (Exception e) {
            if (isVmConfigMissing(e)) {
                log.warn("[DeleteVmCron] PVE删除时配置已不存在，按删除成功处理: TaskId={}, NodeID={}, HostId={}, VmId={}",
                        task.getId(), task.getNodeid(), task.getHostid(), task.getVmid());
                return;
            }
            throw e;
        }
    }

    private void finishDeletedVm(Task task, Vmhost vmhost) {
        deleteSecurityGroups(vmhost);
        releaseSubnetpoolByVmhost(vmhost);
        if (!Boolean.TRUE.equals(vmhostService.clearVmhostVpcIpBinding(vmhost.getId()))) {
            throw new IllegalStateException("清理虚拟机 VPC IP 绑定失败，停止释放公网 IP");
        }
        ippoolService.releaseIppoolByNodeIdAndVmId(vmhost.getNodeid(), vmhost.getVmid(), vmhost.getIpList());
        vmhost.setDeleteState(2);
        vmhost.setExpirationTime(System.currentTimeMillis());
        vmhostService.updateById(vmhost);
        task.setStatus(2);
        task.setError(null);
        taskService.updateById(task);
    }

    private void processRecycleVm() {
        QueryWrapper<Vmhost> queryWrap = new QueryWrapper<>();
        // 筛选expirationTime小于等于当前时间
        queryWrap.le("expiration_time", System.currentTimeMillis());
        // 分页获取1000行节点实例
        Page<Vmhost> page = vmhostService.selectPageByDelete(1,1000,queryWrap);
        List<Vmhost> vmList = page.getRecords();
        for (Vmhost vmhost : vmList){
            // 不依据主控数据库中的中间状态判断，由删除任务读取PVE实时状态。
            // PVE stopped 直接删除，PVE running 才创建强制停止任务，其他过渡状态等待重试。
            enqueueDeleteTask(vmhost);
        }
    }

    private void enqueueDeleteTask(Vmhost vmhost) {
        QueryWrapper<Task> activeDeleteQuery = new QueryWrapper<>();
        activeDeleteQuery.eq("type", DELETE_VM)
                .eq("hostid", vmhost.getId())
                .in("status", 0, 1)
                .last("LIMIT 1");
        if (taskService.getOne(activeDeleteQuery) != null) {
            log.info("[DeleteVmCron] 删除任务已存在，跳过重复创建: HostId={}, VmId={}", vmhost.getId(), vmhost.getVmid());
            return;
        }
        UnifiedResultDto<Object> resultDto = vmhostService.deleteVm(Long.valueOf(vmhost.getId()));
        if (resultDto.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            log.warn("[DeleteVmCron] 创建删除任务失败: HostId={}, VmId={}, Message={}",
                    vmhost.getId(), vmhost.getVmid(), resultDto.getResultCode().getMessage());
        } else {
            log.info("[DeleteVmCron] 创建删除回收站的虚拟机任务: HostId={}, VmId={}", vmhost.getId(), vmhost.getVmid());
        }
    }

    /**
     * 获取或创建强制关机任务，避免删除流程只调用PVE接口却没有可追踪的停止任务。
     */
    private Task ensureForceStopTask(Vmhost vmhost) {
        QueryWrapper<Task> stopQuery = new QueryWrapper<>();
        stopQuery.eq("type", STOP_VM_FORCE)
                .eq("hostid", vmhost.getId())
                .eq("vmid", vmhost.getVmid())
                .in("status", 0, 1)
                .orderByAsc("create_date")
                .last("LIMIT 1");
        Task existing = taskService.getOne(stopQuery);
        if (existing != null) {
            log.info("[DeleteVmCron] 停止任务已存在: StopTaskId={}, HostId={}, VmId={}, Status={}",
                    existing.getId(), vmhost.getId(), vmhost.getVmid(), existing.getStatus());
            return existing;
        }
        Task stopTask = new Task();
        stopTask.setNodeid(vmhost.getNodeid());
        stopTask.setVmid(vmhost.getVmid());
        stopTask.setHostid(vmhost.getId());
        stopTask.setType(STOP_VM_FORCE);
        stopTask.setStatus(0);
        stopTask.setCreateDate(System.currentTimeMillis());
        if (!taskService.save(stopTask)) {
            return null;
        }
        log.info("[DeleteVmCron] 创建停止任务: StopTaskId={}, HostId={}, VmId={}",
                stopTask.getId(), vmhost.getId(), vmhost.getVmid());
        return stopTask;
    }
}
