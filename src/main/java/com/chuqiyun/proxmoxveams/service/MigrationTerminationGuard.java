package com.chuqiyun.proxmoxveams.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.constant.TaskType;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @Author: 星禾
 * @Description: 失败迁移终止前只读核验两端执行状态和目标资源，避免释放仍被占用的预留。
 * @DateTime: 2026/10/30 16:34
 */
@Component
public class MigrationTerminationGuard {
    private static final Set<String> TERMINABLE_STEPS = Set.of("BACKUP", "RESTORE");
    private static final Set<String> EARLY_CHECKPOINTS = Set.of("PENDING", "BACKUP", "RESTORE");
    private static final Set<String> IDLE_AGENT_STATES = Set.of("SUCCESS", "FAILED", "NOT_FOUND");

    @Resource
    private MasterService masterService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private ConfigService configService;

    public void verify(Task task, Map<Object, Object> params) {
        if (task == null || task.getId() == null || !TaskType.MIGRATE_VM.equals(task.getType())
                || !Integer.valueOf(3).equals(task.getStatus()) || params == null) {
            throw new IllegalStateException("只有失败的迁移任务可以终止");
        }
        if (!TERMINABLE_STEPS.contains(String.valueOf(params.get("lastFailedStep")))
                || Boolean.parseBoolean(String.valueOf(params.get("targetCommitted")))
                || params.containsKey("targetNetwork")
                || (params.get("checkpointStage") != null
                && !EARLY_CHECKPOINTS.contains(String.valueOf(params.get("checkpointStage"))))) {
            throw new IllegalStateException("迁移已进入网络重建或目标提交阶段，不能终止，请从原任务继续处理");
        }
        Integer sourceNodeId = positiveInteger(params.get("sourceNodeId"));
        Integer sourceVmid = positiveInteger(params.get("sourceVmid"));
        Integer targetNodeId = positiveInteger(params.get("targetNodeId"));
        Integer targetVmid = positiveInteger(params.get("targetVmid"));
        if (sourceNodeId == null || sourceVmid == null || targetNodeId == null || targetVmid == null
                || targetVmid < 100 || Objects.equals(sourceNodeId, targetNodeId)) {
            throw new IllegalStateException("迁移任务源端或目标参数不完整，不能安全终止");
        }
        Vmhost host = task.getHostid() == null ? null : vmhostService.getById(task.getHostid());
        if (host == null || !Objects.equals(host.getNodeid(), sourceNodeId)
                || !Objects.equals(host.getVmid(), sourceVmid)) {
            throw new IllegalStateException("虚拟机位置已改变，不能释放原迁移任务的资源预留");
        }
        Master target = masterService.getById(targetNodeId);
        if (target == null || target.getNodeName() == null || target.getNodeName().isBlank()) {
            throw new IllegalStateException("目标节点不存在或节点名称为空，不能确认目标资源已清理");
        }
        // 已从主控删除的源节点无需连接；存在但无法连接的节点不能视作空闲。
        Master source = masterService.getById(sourceNodeId);
        if (source != null) {
            verifyAgentIdle(source, task.getId());
        }
        verifyAgentIdle(target, task.getId());
        verifyTargetAbsent(target, targetVmid);
    }

    private void verifyAgentIdle(Master node, Integer taskId) {
        JSONObject response;
        try {
            response = requestStatus(node, String.valueOf(taskId));
        } catch (RuntimeException error) {
            throw new IllegalStateException("无法确认节点" + node.getId() + "的迁移执行状态，请恢复连接后重试");
        }
        JSONObject data = response == null ? null : response.getJSONObject("data");
        if (response == null || !Integer.valueOf(200).equals(response.getInteger("code")) || data == null
                || !IDLE_AGENT_STATES.contains(String.valueOf(data.get("status")))) {
            throw new IllegalStateException("节点" + node.getId() + "的迁移任务仍在运行或状态未知，不能终止");
        }
    }

    private void verifyTargetAbsent(Master target, Integer targetVmid) {
        JSONObject response;
        JSONObject nodeResponse;
        try {
            ProxmoxApiUtil api = newProxmoxApi();
            HashMap<String, String> cookies = masterService.getMasterCookieMap(target.getId());
            response = api.getNodeApi(target, cookies, "/cluster/resources", new HashMap<>());
            nodeResponse = api.getNodeApi(target, cookies, "/nodes", new HashMap<>());
        } catch (RuntimeException error) {
            throw new IllegalStateException("无法查询目标PVE集群资源，不能释放目标VMID预留");
        }
        JSONArray resources = response == null ? null : response.getJSONArray("data");
        JSONArray nodes = nodeResponse == null ? null : nodeResponse.getJSONArray("data");
        if (resources == null || nodes == null || nodes.isEmpty()) {
            throw new IllegalStateException("无法查询目标PVE集群资源，不能释放目标VMID预留");
        }
        boolean targetPresent = false;
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            String name = node == null ? null : node.getString("node");
            if (name == null || name.isBlank()) {
                throw new IllegalStateException("目标PVE集群节点信息不完整，不能释放目标VMID预留");
            }
            targetPresent |= target.getNodeName().equals(name);
        }
        if (!targetPresent) {
            throw new IllegalStateException("目标PVE集群节点身份与主控不一致，不能释放目标VMID预留");
        }
        for (int i = 0; i < resources.size(); i++) {
            JSONObject resource = resources.getJSONObject(i);
            if (resource == null || resource.getString("type") == null) {
                throw new IllegalStateException("目标PVE集群资源信息不完整，不能释放目标VMID预留");
            }
            if ("qemu".equals(resource.getString("type")) || "lxc".equals(resource.getString("type"))) {
                Integer vmid = positiveInteger(resource.get("vmid"));
                if (vmid == null || vmid < 100) {
                    throw new IllegalStateException("目标PVE集群VMID信息不完整，不能释放目标VMID预留");
                }
                if (Objects.equals(vmid, targetVmid)) {
                    throw new IllegalStateException("目标VMID " + targetVmid
                            + " 仍存在，请先确认并清理该任务残留的目标虚拟机，再终止迁移；源虚拟机请勿删除");
                }
            }
        }
    }

    protected JSONObject requestStatus(Master node, String taskId) {
        return ClientApiUtil.getMigrationStatus(node.getHost(), configService.getToken(), node.getControllerPort(), taskId);
    }

    protected ProxmoxApiUtil newProxmoxApi() {
        return new ProxmoxApiUtil();
    }

    private Integer positiveInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            int result = Integer.parseInt(value.toString());
            return result > 0 ? result : null;
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
