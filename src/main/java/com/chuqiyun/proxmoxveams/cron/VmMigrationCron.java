package com.chuqiyun.proxmoxveams.cron;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
import com.chuqiyun.proxmoxveams.constant.TaskType;
import com.chuqiyun.proxmoxveams.entity.Ippool;
import com.chuqiyun.proxmoxveams.entity.Ipstatus;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Subnet;
import com.chuqiyun.proxmoxveams.entity.Subnetpool;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vncdata;
import com.chuqiyun.proxmoxveams.entity.Vncinfo;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.impl.VmMigrationServiceImpl;
import com.chuqiyun.proxmoxveams.entity.VpcIpBinding;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import com.chuqiyun.proxmoxveams.service.IppoolService;
import com.chuqiyun.proxmoxveams.service.IpstatusService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.NatForwardSyncService;
import com.chuqiyun.proxmoxveams.service.SecurityGroupBusinessService;
import com.chuqiyun.proxmoxveams.service.SubnetService;
import com.chuqiyun.proxmoxveams.service.SubnetpoolService;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmMigrationService;
import com.chuqiyun.proxmoxveams.service.VmIdReservationService;
import com.chuqiyun.proxmoxveams.service.MigrationExecutionLock;
import com.chuqiyun.proxmoxveams.service.VncdataService;
import com.chuqiyun.proxmoxveams.service.VncinfoService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.service.VpcIpBindingService;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import com.chuqiyun.proxmoxveams.utils.CloudInitNetworkUtil;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import com.chuqiyun.proxmoxveams.utils.VmUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@EnableScheduling
public class VmMigrationCron {
    private static final String NETWORK_TYPE_VPC = "vpc";
    private static final int IP_VERSION_4 = 4;
    private static final int IP_VERSION_6 = 6;
    private static final int VPC_BINDING_STATUS_ACTIVE = 1;
    private static final int VPC_BINDING_STATUS_DELETED = 0;
    private static final String DEFAULT_CREATE_VM_STORAGE = "local-lvm";

    @Resource
    private TaskService taskService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private MasterService masterService;
    @Resource
    private ConfigService configService;
    @Resource
    private IppoolService ippoolService;
    @Resource
    private IpstatusService ipstatusService;
    @Resource
    private SubnetpoolService subnetpoolService;
    @Resource
    private SubnetService subnetService;
    @Resource
    private VpcIpBindingService vpcIpBindingService;
    @Resource
    private NatForwardSyncService natForwardSyncService;
    @Resource
    private SecurityGroupBusinessService securityGroupBusinessService;
    @Resource
    private VncinfoService vncinfoService;
    @Resource
    private VncdataService vncdataService;
    @Resource
    private VmMigrationService vmMigrationService;
    @Resource
    private VmIdReservationService vmIdReservationService;
    @Resource
    private MigrationExecutionLock migrationExecutionLock;
    @Resource
    private PlatformTransactionManager transactionManager;

    @Async("workflowExecutor")
    @Scheduled(fixedDelay = 5000)
    public void vmMigrationCron() {
        migrationExecutionLock.runLocked(this::runNextTask);
    }

    private void runNextTask() {
        // 会话锁覆盖整个流程。获得锁后仍为 running 的任务来自进程中断，优先恢复。
        QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("type", TaskType.MIGRATE_VM);
        queryWrapper.in("status", 0, 1);
        queryWrapper.orderByDesc("status");
        queryWrapper.orderByAsc("id");
        Page<Task> taskPage = taskService.getTaskList(1, 1, queryWrapper);
        if (taskPage == null || taskPage.getRecords().isEmpty()) {
            return;
        }
        Task task = taskPage.getRecords().get(0);
        Integer previousStatus = task.getStatus();
        if (!taskService.update(new UpdateWrapper<Task>().eq("id", task.getId())
                .eq("status", previousStatus).set("status", 1))) {
            return;
        }
        task.setStatus(1);
        try {
            execute(task);
        } catch (Exception e) {
            Map<Object, Object> params = ensureParams(task);
            if (params.get("vmIdReservationOwner") != null && !params.containsKey("lastFailedStep")) {
                String stage = stringValue(params.get("stage"));
                params.put("lastFailedStep", VmMigrationServiceImpl.STAGES.contains(stage) && !"PENDING".equals(stage)
                        ? stage : "BACKUP");
            }
            failTask(task, e.getMessage());
        }
    }

    /**
     * 迁移按阶段推进。每个阶段成功后都会把进度写回任务参数，
     * 失败重试时从 lastFailedStep 继续，已完成的重活（打包、导入）不会重来一遍。
     */
    private void execute(Task task) throws Exception {
        Map<Object, Object> params = ensureParams(task);
        Vmhost vmhost = vmhostService.getById(task.getHostid());
        if (vmhost == null) {
            throw new IllegalStateException("VM does not exist");
        }
        Integer sourceNodeId = toInteger(params.get("sourceNodeId"));
        Integer targetNodeId = toInteger(params.get("targetNodeId"));
        Integer sourceVmid = toInteger(params.get("sourceVmid"));
        Integer targetVmid = toInteger(params.get("targetVmid"));
        String targetStorage = stringValue(params.get("targetStorage"));
        String backupDir = stringValue(params.get("backupDir"));
        if (sourceNodeId == null || targetNodeId == null || sourceVmid == null) {
            throw new IllegalStateException("migration task params are incomplete");
        }
        Master sourceNode = masterService.getById(sourceNodeId);
        Master targetNode = masterService.getById(targetNodeId);
        if (sourceNode == null || targetNode == null) {
            throw new IllegalStateException("source or target node does not exist");
        }
        targetStorage = resolveTargetStorage(targetStorage, targetNode);
        params.put("targetStorage", targetStorage);
        String reservationOwner = stringValue(params.get("vmIdReservationOwner"));
        if (targetVmid == null || reservationOwner == null) {
            throw new IllegalStateException("旧迁移任务缺少VMID预留，请取消并重新提交迁移");
        }
        vmIdReservationService.assertOwned(targetNodeId, targetVmid, reservationOwner);
        task.setParams(params);
        persistTask(task);

        final Integer finalSourceNodeId = sourceNodeId;
        final Integer finalTargetNodeId = targetNodeId;
        final Integer finalTargetVmid = targetVmid;
        final Integer finalSourceVmid = sourceVmid;
        final String finalTargetStorage = targetStorage;
        final String token = configService.getToken();
        if (StringUtils.isBlank(sourceNode.getNodePublicKey())) {
            throw new IllegalStateException("源节点尚未登记QAgent证书，无法建立迁移授信通道");
        }
        requireMigrationCapabilities(sourceNode, token);
        requireMigrationCapabilities(targetNode, token);
        revokeTransferChannel(task, params, sourceNode, token);
        // 续跑时沿用任务里已保存的源端快照，避免上一轮已把数据库切到目标端后重新取到错的数据
        VmhostSnapshot sourceSnapshot = restoreSnapshot(params);
        if (sourceSnapshot == null) {
            sourceSnapshot = VmhostSnapshot.of(vmhost);
            params.put("sourceSnapshot", sourceSnapshot.toMap());
            task.setParams(params);
            persistTask(task);
        }
        List<NatRule> oldNatRules = savedNatRules(params, sourceSnapshot);
        List<VpcIpBinding> oldVpcBindings = savedVpcBindings(params, sourceSnapshot);
        persistTask(task);
        final VmhostSnapshot finalSourceSnapshot = sourceSnapshot;
        final List<NatRule> finalNatRules = oldNatRules;
        final List<VpcIpBinding> finalVpcBindings = oldVpcBindings;

        String resumeFrom = stringValue(params.get("resumeFromStage"));
        if (resumeFrom == null) {
            String checkpoint = stringValue(params.get("checkpointStage"));
            int next = stageIndex(checkpoint) + 1;
            if (checkpoint != null && next > 0 && next < VmMigrationServiceImpl.STAGES.size()) {
                resumeFrom = VmMigrationServiceImpl.STAGES.get(next);
            }
        }
        int startIndex = resumeFrom == null ? 0 : stageIndex(resumeFrom);
        if (!Boolean.TRUE.equals(params.get("targetCommitted")) && Boolean.TRUE.equals(params.get("networkRolledBack"))) {
            startIndex = Math.min(startIndex, stageIndex("NETWORK_REBIND"));
        }
        // 未提交的网络变更先按持久化清单回收，再完整重建；不能复用已回滚的网络检查点。
        if (!Boolean.TRUE.equals(params.get("targetCommitted")) && params.containsKey("targetNetwork")) {
            rollbackJournal(task, params, finalSourceSnapshot, finalVpcBindings);
            if (startIndex > stageIndex("NETWORK_REBIND")) {
                startIndex = stageIndex("NETWORK_REBIND");
            }
        }
        if (startIndex < 0) {
            params.remove("resumeFromStage");
            startIndex = 0;
            task.setParams(params);
        } else if (resumeFrom != null) {
            appendLog(task, params, "从断点继续: " + resumeFrom);
            params.remove("resumeFromStage");
            task.setParams(params);
            persistTask(task);
        }

        migrateStage(task, params, startIndex, "BACKUP", 1, "源节点开始打包", () -> {
            // 被控端已存在可用镜像时会直接复用，不重新 vzdump
            requireAgentAccepted(requestMigrationBackup(sourceNode, token, String.valueOf(task.getId()), finalSourceVmid, backupDir));
            JSONObject backupData = waitAgentStage(task, sourceNode, token, String.valueOf(task.getId()), "BACKUP_DONE", 1, 35);
            if (toInteger(params.get("targetVmid")) == null) {
                throw new IllegalStateException("failed to allocate target VMID");
            }
            params.put("sourceBackupFile", getFileName(backupData.getString("backup_file")));
            params.put("sourceBackupSize", backupData.getLong("backup_size"));
            params.put("sourceBackupSha256", backupData.getString("backup_sha256"));
            return null;
        });

        migrateStage(task, params, startIndex, "RESTORE", 35, "目标节点开始导入", () -> {
            String backupFileName = getFileName(stringValue(params.get("sourceBackupFile")));
            Number size = params.get("sourceBackupSize") instanceof Number n ? n : null;
            String sha256 = stringValue(params.get("sourceBackupSha256"));
            if (size == null || size.longValue() <= 0 || sha256 == null || !sha256.matches("[0-9a-fA-F]{64}")) {
                throw new IllegalStateException("源备份缺少大小或SHA256，需重新完成备份阶段");
            }
            revokeTransferChannel(task, params, sourceNode, token);
            JSONObject opened = requestMigrationChannel(sourceNode, token, String.valueOf(task.getId()), "create", null);
            requireAgentAccepted(opened);
            JSONObject grant = opened.getJSONObject("data");
            String channelId = grant == null ? null : grant.getString("channel_id");
            if (channelId == null || !channelId.matches("[A-Za-z0-9_-]{16,128}")) {
                throw new IllegalStateException("迁移通道标识无效");
            }
            params.put("sourceTransferChannel", channelId);
            try {
                persistTask(task);
                String path = grant.getString("path");
                if (path == null || !path.equals("/migration-transfer/" + channelId + "/" + backupFileName)
                        || grant.getLongValue("size") != size.longValue()
                        || !sha256.equalsIgnoreCase(grant.getString("sha256"))) {
                    throw new IllegalStateException("迁移通道归档身份与源备份不一致");
                }
                String sourceUrl = "https://" + sourceNode.getHost() + ":" + sourceNode.getControllerPort() + path;
                requireAgentAccepted(requestMigrationRestore(targetNode, token,
                        String.valueOf(task.getId()), sourceUrl, grant.getString("token"), finalTargetVmid,
                        finalTargetStorage, backupDir, sourceNode.getNodePublicKey(), size.longValue(), sha256));
                waitAgentStage(task, targetNode, token, String.valueOf(task.getId()), "RESTORE_DONE", 35, 88);
            } finally {
                revokeTransferChannel(task, params, sourceNode, token);
            }
            return null;
        });

        migrateStage(task, params, startIndex, "NETWORK_REBIND", 88,
                "重建目标端网络与端口转发", () -> {
                    requireTargetStopped(targetNode, finalTargetVmid);
                    Vmhost current = vmhostService.getById(task.getHostid());
                    NetworkMigrationResult result = new TransactionTemplate(transactionManager).execute(transaction -> {
                        NetworkMigrationResult allocated = allocateTargetNetwork(current, finalSourceSnapshot, targetNode,
                                finalTargetNodeId, finalTargetVmid);
                        allocated.targetNodeId = finalTargetNodeId;
                        allocated.targetVmid = finalTargetVmid;
                        params.remove("networkRolledBack");
                        saveNetworkJournal(task, params, allocated);
                        return allocated;
                    });
                    new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                        switchVmhostToTarget(current, finalSourceSnapshot, result, finalTargetNodeId,
                                finalTargetVmid, finalTargetStorage);
                        saveNetworkJournal(task, params, result);
                    });
                    syncTargetCloudInit(current, targetNode, result);
                    migrateNetworkForwards(task, params, current, sourceNode, targetNode, finalNatRules, finalVpcBindings, result, token);
                    syncSecurityGroupAfterMigration(current);
                    invalidateVncCache(current);
                    vmMigrationService.appendTaskLog(task, params, "目标端网络已重建: "
                            + String.join(",", result.ipList));
                    return result;
                });

        migrateStage(task, params, startIndex, "START_TARGET_VM", 92, "启动目标虚拟机", () -> {
            Vmhost current = requireCurrentVmhost(task);
            if (!Objects.equals(current.getNodeid(), finalTargetNodeId)
                    || !Objects.equals(current.getVmid(), finalTargetVmid)) {
                throw new IllegalStateException("target VM network has not been rebound");
            }
            // 在可能启动目标之前持久化提交边界；此后失败只向前重试，保留两端资源。
            params.put("targetCommitted", Boolean.TRUE);
            persistTask(task);
            boolean start = !Boolean.FALSE.equals(params.get("startAfterMigration"));
            if (start) {
                startTargetVm(targetNode, finalTargetNodeId, finalTargetVmid);
                current.setStatus(0);
                if (!vmhostService.updateById(current)) {
                    throw new IllegalStateException("目标虚拟机状态保存失败");
                }
                createApplyWindowsVmIpTaskIfNeeded(current);
            }
            return null;
        });

        migrateStage(task, params, startIndex, "CLEANUP_SOURCE", 95, "清理源虚拟机", () -> {
            JSONObject cleanupResult = requestCleanupMigrationSource(sourceNode, token, String.valueOf(task.getId()), finalSourceVmid);
            JSONObject cleanupData = cleanupResult == null ? null : cleanupResult.getJSONObject("data");
            if (cleanupResult == null || cleanupResult.getInteger("code") == null || cleanupResult.getInteger("code") != 200
                    || cleanupData == null || !"SUCCESS".equals(cleanupData.getString("status"))) {
                throw new IllegalStateException("failed to cleanup source VM");
            }
            return null;
        });

        migrateStage(task, params, startIndex, "RELEASE_SOURCE_NETWORK", 98, "回收源端网络资源", () -> {
            releaseSourceNetwork(finalSourceSnapshot, sourceNode, finalNatRules, finalVpcBindings, token);
            return null;
        });

        updateProgress(task, "DONE", 100, null, "迁移完成");
        task.setStatus(2);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                persistTask(task);
                vmIdReservationService.release(reservationOwner);
            });
        } catch (RuntimeException e) {
            params.put("lastFailedStep", "RELEASE_SOURCE_NETWORK");
            throw e;
        }
        UnifiedLogger.log(UnifiedLogger.LogType.TASK_MIGRATE_VM, "VM migration success, taskId=" + task.getId());
    }

    private int stageIndex(String stage) {
        return VmMigrationServiceImpl.STAGES.indexOf(stage);
    }

    /**
     * 阶段执行器：已完成（断点之前的阶段）直接跳过；失败时记录失败阶段并回滚未提交的目标资源。
     */
    private <T> T migrateStage(Task task, Map<Object, Object> params, int startIndex, String stage, int progress,
                               String message, StageAction<T> action) throws Exception {
        assertExecutionLock();
        int currentIndex = stageIndex(stage);
        if (currentIndex < startIndex) {
            appendLog(task, params, "跳过已完成步骤: " + stage);
            task.setParams(params);
            persistTask(task);
            return null;
        }
        updateProgress(task, stage, progress, null, message);
        try {
            params.remove("lastFailedStep");
            T result = action.run();
            params.put("checkpointStage", stage);
            appendLog(task, params, "步骤完成: " + stage);
            task.setParams(params);
            persistTask(task);
            return result;
        } catch (Exception e) {
            handleStageFailure(task, params, stage, e);
            throw e;
        }
    }

    private void handleStageFailure(Task task, Map<Object, Object> params, String stage, Exception e) {
        params.put("lastFailedStep", stage);
        appendLog(task, params, "步骤失败: " + stage + ", " + e.getMessage());
        task.setParams(params);
        persistTask(task);
        if (!Boolean.TRUE.equals(params.get("targetCommitted"))) {
            Vmhost vmhost = vmhostService.getById(task.getHostid());
            if (vmhost != null) {
                VmhostSnapshot snapshot = restoreSnapshot(params);
                if (snapshot != null) {
                    rollbackJournal(task, params, snapshot, savedVpcBindings(params, snapshot));
                }
            }
        }
    }

    private void assertExecutionLock() {
        if (migrationExecutionLock != null) {
            migrationExecutionLock.assertHeld();
        }
    }

    private void persistTask(Task task) {
        assertExecutionLock();
        if (!taskService.updateTask(task)) {
            throw new IllegalStateException("迁移任务检查点保存失败");
        }
    }

    private void requireMigrationCapabilities(Master node, String token) {
        JSONObject response = requestMigrationCapabilities(node, token);
        requireAgentAccepted(response);
        JSONObject data = response.getJSONObject("data");
        if (data == null || data.getIntValue("protocol") < 2 || !data.getBooleanValue("temporary_channel")
                || !data.getBooleanValue("safe_restore") || !data.getBooleanValue("source_identity")) {
            throw new IllegalStateException("节点 " + node.getId() + " 的QAgent不支持安全迁移，请先升级被控");
        }
    }

    protected JSONObject requestMigrationCapabilities(Master node, String token) {
        return ClientApiUtil.getMigrationCapabilities(node, token);
    }

    protected JSONObject requestMigrationBackup(Master node, String token, String taskId, Integer vmid, String backupDir) {
        return ClientApiUtil.startMigrationBackup(node.getHost(), token, node.getControllerPort(), taskId, vmid, backupDir);
    }

    protected JSONObject requestMigrationRestore(Master target, String token, String taskId, String sourceUrl,
                                                  String sourceToken, Integer targetVmid, String storage, String backupDir,
                                                  String certificate, Long size, String sha256) {
        return ClientApiUtil.startMigrationRestore(target.getHost(), token, target.getControllerPort(), taskId,
                sourceUrl, sourceToken, targetVmid, storage, backupDir, certificate, size, sha256);
    }

    protected JSONObject requestMigrationStatus(Master node, String token, String taskId) {
        return ClientApiUtil.getMigrationStatus(node.getHost(), token, node.getControllerPort(), taskId);
    }

    protected JSONObject requestCleanupMigrationSource(Master node, String token, String taskId, Integer vmid) {
        return ClientApiUtil.cleanupMigrationSource(node.getHost(), token, node.getControllerPort(), taskId, vmid);
    }

    private void requireAgentAccepted(JSONObject response) {
        if (response == null || !Integer.valueOf(200).equals(response.getInteger("code"))) {
            throw new IllegalStateException("QAgent迁移请求失败: "
                    + (response == null ? "节点未响应" : response.getString("message")));
        }
    }

    protected JSONObject requestMigrationChannel(Master source, String token, String taskId, String action, String channelId) {
        return ClientApiUtil.migrationChannel(source, token, taskId, action, channelId);
    }

    private void revokeTransferChannel(Task task, Map<Object, Object> params, Master source, String token) {
        String channelId = stringValue(params.get("sourceTransferChannel"));
        if (channelId != null) {
            requireAgentAccepted(requestMigrationChannel(source, token, String.valueOf(task.getId()), "revoke", channelId));
            params.remove("sourceTransferChannel");
            persistTask(task);
        }
    }

    private void saveNetworkJournal(Task task, Map<Object, Object> params, NetworkMigrationResult result) {
        params.put("targetNetwork", JSON.parseObject(JSON.toJSONString(result, JSONWriter.Feature.FieldBased)));
        task.setParams(params);
        persistTask(task);
    }

    private NetworkMigrationResult restoreNetworkJournal(Map<Object, Object> params) {
        Object value = params.get("targetNetwork");
        return value == null ? null : JSON.parseObject(JSON.toJSONString(value),
                NetworkMigrationResult.class, JSONReader.Feature.FieldBased);
    }

    private List<NatRule> savedNatRules(Map<Object, Object> params, VmhostSnapshot snapshot) {
        if (!params.containsKey("sourceNatRules")) {
            params.put("sourceNatRules", JSON.parseArray(JSON.toJSONString(fetchNatRules(snapshot), JSONWriter.Feature.FieldBased)));
        }
        JSONArray values = JSON.parseArray(JSON.toJSONString(params.get("sourceNatRules")));
        List<NatRule> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            result.add(JSON.parseObject(values.getJSONObject(i).toJSONString(), NatRule.class, JSONReader.Feature.FieldBased));
        }
        return result;
    }

    private List<VpcIpBinding> savedVpcBindings(Map<Object, Object> params, VmhostSnapshot snapshot) {
        if (!params.containsKey("sourceVpcBindings")) {
            params.put("sourceVpcBindings", JSON.parseArray(JSON.toJSONString(fetchActiveVpcBindings(snapshot))));
        }
        return JSON.parseArray(JSON.toJSONString(params.get("sourceVpcBindings")), VpcIpBinding.class);
    }

    private void rollbackJournal(Task task, Map<Object, Object> params, VmhostSnapshot snapshot,
                                 List<VpcIpBinding> oldBindings) {
        NetworkMigrationResult result = restoreNetworkJournal(params);
        if (result == null) {
            return;
        }
        Vmhost current = requireCurrentVmhost(task);
        Object savedJournal = params.get("targetNetwork");
        Object checkpoint = params.get("checkpointStage");
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                rollbackTargetNetwork(current, snapshot, result, oldBindings);
                params.remove("targetNetwork");
                params.put("checkpointStage", "RESTORE");
                params.put("networkRolledBack", true);
                persistTask(task);
            });
        } catch (RuntimeException e) {
            // 数据库回滚时也恢复内存清单，后续 failTask 不能把尚未回收的资源信息覆盖掉。
            params.put("targetNetwork", savedJournal);
            if (checkpoint == null) {
                params.remove("checkpointStage");
            } else {
                params.put("checkpointStage", checkpoint);
            }
            params.remove("networkRolledBack");
            throw e;
        }
    }

    private Vmhost requireCurrentVmhost(Task task) {
        Vmhost vmhost = vmhostService.getById(task.getHostid());
        if (vmhost == null) {
            throw new IllegalStateException("VM does not exist");
        }
        return vmhost;
    }

    @FunctionalInterface
    private interface StageAction<T> {
        T run() throws Exception;
    }

    protected ProxmoxApiUtil newProxmoxApi() {
        return new ProxmoxApiUtil();
    }

    private NetworkMigrationResult allocateTargetNetwork(Vmhost vmhost, VmhostSnapshot sourceSnapshot, Master targetNode,
                                                         Integer targetNodeId, Integer targetVmid) {
        if (isVpcNetwork(vmhost)) {
            return allocateTargetVpcNetwork(vmhost, sourceSnapshot, targetNode, targetNodeId, targetVmid);
        }
        return allocateTargetClassicNetwork(vmhost, sourceSnapshot, targetNode, targetNodeId, targetVmid);
    }

    private NetworkMigrationResult allocateTargetClassicNetwork(Vmhost vmhost, VmhostSnapshot sourceSnapshot, Master targetNode,
                                                                Integer targetNodeId, Integer targetVmid) {
        NetworkMigrationResult result = new NetworkMigrationResult();
        result.vpc = false;
        result.targetBridge = resolveClassicBridge(vmhost, targetNode);

        List<String> oldIpv4List = CloudInitNetworkUtil.getIpv4List(sourceSnapshot.ipConfig);
        List<String> oldIpv6List = CloudInitNetworkUtil.getIpv6List(sourceSnapshot.ipConfig);
        if (oldIpv4List.isEmpty() && oldIpv6List.isEmpty() && sourceSnapshot.ipList != null) {
            for (String ip : sourceSnapshot.ipList) {
                if (StringUtils.isBlank(ip)) {
                    continue;
                }
                if (ip.contains(":")) {
                    oldIpv6List.add(ip);
                } else {
                    oldIpv4List.add(ip);
                }
            }
        }
        if (oldIpv4List.isEmpty() && oldIpv6List.isEmpty()) {
            throw new IllegalStateException("source VM has no IP config");
        }

        List<Ippool> ipv4List = new ArrayList<>();
        List<Ippool> ipv6List = new ArrayList<>();
        Set<String> selectedIpv4Set = new LinkedHashSet<>();
        Set<String> selectedIpv6Set = new LinkedHashSet<>();
        for (int i = 0; i < oldIpv4List.size(); i++) {
            Ippool ippool = allocateOneClassicIp(targetNodeId, targetNode, vmhost.getIfnat(), targetVmid, IP_VERSION_4, selectedIpv4Set);
            ipv4List.add(ippool);
            selectedIpv4Set.add(ippool.getIp());
            result.targetIppools.add(ippool);
        }
        for (int i = 0; i < oldIpv6List.size(); i++) {
            Ippool ippool = allocateOneClassicIp(targetNodeId, targetNode, vmhost.getIfnat(), targetVmid, IP_VERSION_6, selectedIpv6Set);
            ipv6List.add(ippool);
            selectedIpv6Set.add(ippool.getIp());
            result.targetIppools.add(ippool);
        }

        result.ipConfig = buildClassicIpConfig(ipv4List, ipv6List);
        result.ipList = CloudInitNetworkUtil.getIpList(result.ipConfig);
        result.primaryIpv4 = ipv4List.isEmpty() ? null : ipv4List.get(0).getIp();
        result.nameservers = buildClassicNameservers(result.targetIppools);
        return result;
    }

    private NetworkMigrationResult allocateTargetVpcNetwork(Vmhost vmhost, VmhostSnapshot sourceSnapshot, Master targetNode,
                                                            Integer targetNodeId, Integer targetVmid) {
        NetworkMigrationResult result = new NetworkMigrationResult();
        result.vpc = true;

        Subnet targetSubnet = resolveTargetVpcSubnet(sourceSnapshot, targetNodeId);
        if (targetSubnet == null) {
            throw new IllegalStateException("target node has no matching VPC subnet");
        }
        result.targetVpcSubnetId = targetSubnet.getId();
        result.targetBridge = targetSubnet.getVnet();

        int privateIpCount = CloudInitNetworkUtil.getIpList(sourceSnapshot.ipConfig).size();
        if (privateIpCount <= 0 && sourceSnapshot.ipList != null) {
            privateIpCount = sourceSnapshot.ipList.size();
        }
        if (privateIpCount <= 0) {
            throw new IllegalStateException("source VPC VM has no private IP");
        }

        Set<String> selectedPrivateIpSet = new LinkedHashSet<>();
        for (int i = 0; i < privateIpCount; i++) {
            Subnetpool subnetpool = allocateOneVpcPrivateIp(targetNodeId, targetSubnet.getId(), targetVmid, selectedPrivateIpSet);
            result.targetVpcPrivateIps.add(subnetpool);
            selectedPrivateIpSet.add(subnetpool.getIp());
        }

        Set<String> selectedPublicIpSet = new LinkedHashSet<>();
        for (int i = 0; i < privateIpCount; i++) {
            Ippool publicIp = allocateOneVpcPublicIp(targetNodeId, targetNode, targetVmid, selectedPublicIpSet);
            result.targetVpcPublicIps.add(publicIp);
            selectedPublicIpSet.add(publicIp.getIp());
        }

        result.ipConfig = buildVpcIpConfig(result.targetVpcPrivateIps);
        result.ipList = CloudInitNetworkUtil.getIpList(result.ipConfig);
        result.primaryIpv4 = result.ipList.isEmpty() ? null : result.ipList.get(0);
        result.nameservers = buildVpcNameservers(result.targetVpcPrivateIps);
        return result;
    }

    private Ippool allocateOneClassicIp(Integer targetNodeId, Master targetNode, Integer ifnat, Integer targetVmid,
                                        Integer ipVersion, Set<String> excludeIpSet) {
        Set<Integer> skippedIds = new LinkedHashSet<>();
        while (true) {
            QueryWrapper<Ippool> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("node_id", targetNodeId);
            queryWrapper.eq("status", 0);
            queryWrapper.eq("ip_version", ipVersion);
            if (Objects.equals(ifnat, 1) && Objects.equals(ipVersion, IP_VERSION_4) && targetNode.getNatippool() != null) {
                queryWrapper.eq("pool_id", targetNode.getNatippool());
            } else if (targetNode.getNatippool() != null) {
                queryWrapper.ne("pool_id", targetNode.getNatippool());
            }
            if (excludeIpSet != null && !excludeIpSet.isEmpty()) {
                queryWrapper.notIn("ip", excludeIpSet);
            }
            if (!skippedIds.isEmpty()) {
                queryWrapper.notIn("id", skippedIds);
            }
            queryWrapper.orderByAsc("id");
            queryWrapper.last("limit 1");
            Ippool ippool = ippoolService.getOne(queryWrapper);
            if (ippool == null) {
                throw new IllegalStateException("target node has no free IPv" + ipVersion + " address");
            }
            if (ippoolService.bindFreeIppool(ippool.getId(), targetVmid)) {
                ippool.setStatus(1);
                ippool.setVmId(targetVmid);
                return ippool;
            }
            skippedIds.add(ippool.getId());
        }
    }

    private Subnetpool allocateOneVpcPrivateIp(Integer targetNodeId, Integer subnetId, Integer targetVmid, Set<String> excludeIpSet) {
        if (targetNodeId == null || subnetId == null || subnetId <= 0) {
            throw new IllegalStateException("target VPC subnet is invalid");
        }
        Set<Integer> skippedIds = new LinkedHashSet<>();
        while (true) {
            QueryWrapper<Subnetpool> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("node_id", targetNodeId);
            queryWrapper.eq("subnat_id", subnetId);
            queryWrapper.eq("status", 0);
            if (excludeIpSet != null && !excludeIpSet.isEmpty()) {
                queryWrapper.notIn("ip", excludeIpSet);
            }
            if (!skippedIds.isEmpty()) {
                queryWrapper.notIn("id", skippedIds);
            }
            queryWrapper.orderByAsc("id");
            queryWrapper.last("limit 1");
            Subnetpool subnetpool = subnetpoolService.getOne(queryWrapper);
            if (subnetpool == null) {
                throw new IllegalStateException("target VPC subnet has no free private IP");
            }
            if (bindSubnetpool(subnetpool, targetVmid)) {
                subnetpool.setStatus(1);
                subnetpool.setVmId(targetVmid);
                return subnetpool;
            }
            skippedIds.add(subnetpool.getId());
        }
    }

    private Ippool allocateOneVpcPublicIp(Integer targetNodeId, Master targetNode, Integer targetVmid, Set<String> excludeIpSet) {
        Set<Integer> skippedIds = new LinkedHashSet<>();
        while (true) {
            QueryWrapper<Ippool> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("node_id", targetNodeId);
            queryWrapper.eq("status", 0);
            queryWrapper.eq("ip_version", IP_VERSION_4);
            if (StringUtils.isNotBlank(targetNode.getHost())) {
                queryWrapper.ne("ip", targetNode.getHost().trim());
            }
            if (targetNode.getNatippool() != null) {
                queryWrapper.ne("pool_id", targetNode.getNatippool());
            }
            if (excludeIpSet != null && !excludeIpSet.isEmpty()) {
                queryWrapper.notIn("ip", excludeIpSet);
            }
            if (!skippedIds.isEmpty()) {
                queryWrapper.notIn("id", skippedIds);
            }
            queryWrapper.orderByAsc("id");
            queryWrapper.last("limit 1");
            Ippool ippool = ippoolService.getOne(queryWrapper);
            if (ippool == null) {
                throw new IllegalStateException("target node has no free VPC public IP");
            }
            if (ippoolService.bindFreeIppool(ippool.getId(), targetVmid)) {
                ippool.setStatus(1);
                ippool.setVmId(targetVmid);
                return ippool;
            }
            skippedIds.add(ippool.getId());
        }
    }

    private boolean bindSubnetpool(Subnetpool subnetpool, Integer targetVmid) {
        UpdateWrapper<Subnetpool> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", subnetpool.getId());
        updateWrapper.eq("status", 0);
        updateWrapper.set("status", 1);
        updateWrapper.set("vm_id", targetVmid);
        return subnetpoolService.update(updateWrapper);
    }

    private Map<String, String> buildClassicIpConfig(List<Ippool> ipv4List, List<Ippool> ipv6List) {
        Map<String, String> ipConfig = new LinkedHashMap<>();
        int count = Math.max(ipv4List.size(), ipv6List.size());
        for (int i = 0; i < count; i++) {
            List<String> parts = new ArrayList<>();
            if (i < ipv4List.size()) {
                Ippool ippool = ipv4List.get(i);
                parts.add("ip=" + ippool.getIp() + "/" + getClassicMask(ippool));
                parts.add("gw=" + getClassicGateway(ippool));
            }
            if (i < ipv6List.size()) {
                Ippool ippool = ipv6List.get(i);
                parts.add("ip6=" + ippool.getIp() + "/" + getClassicMask(ippool));
                parts.add("gw6=" + getClassicGateway(ippool));
            }
            ipConfig.put(String.valueOf(i + 1), String.join(",", parts));
        }
        return ipConfig;
    }

    private Map<String, String> buildVpcIpConfig(List<Subnetpool> subnetpoolList) {
        Map<String, String> ipConfig = new LinkedHashMap<>();
        for (int i = 0; i < subnetpoolList.size(); i++) {
            Subnetpool subnetpool = subnetpoolList.get(i);
            ipConfig.put(String.valueOf(i + 1), "ip=" + subnetpool.getIp() + "/" + subnetpool.getMask()
                    + ",gw=" + subnetpool.getGateway());
        }
        return ipConfig;
    }

    private Integer getClassicMask(Ippool ippool) {
        if (StringUtils.isNotBlank(ippool.getSubnetMask())) {
            try {
                return Integer.parseInt(ippool.getSubnetMask().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        Ipstatus ipstatus = ipstatusService.getById(ippool.getPoolId());
        if (ipstatus == null || ipstatus.getMask() == null) {
            throw new IllegalStateException("IP pool mask is missing: ip=" + ippool.getIp());
        }
        return ipstatus.getMask();
    }

    private String getClassicGateway(Ippool ippool) {
        if (StringUtils.isNotBlank(ippool.getGateway())) {
            return ippool.getGateway().trim();
        }
        Ipstatus ipstatus = ipstatusService.getById(ippool.getPoolId());
        if (ipstatus == null || StringUtils.isBlank(ipstatus.getGateway())) {
            throw new IllegalStateException("IP pool gateway is missing: ip=" + ippool.getIp());
        }
        return ipstatus.getGateway().trim();
    }

    private List<String> buildClassicNameservers(List<Ippool> ippoolList) {
        Set<String> nameservers = new LinkedHashSet<>();
        Ippool primaryIppool = getPrimaryDnsIppool(ippoolList);
        if (primaryIppool != null) {
            addNameserver(nameservers, primaryIppool.getDns1());
            addNameserver(nameservers, primaryIppool.getDns2());
            Ipstatus ipstatus = ipstatusService.getById(primaryIppool.getPoolId());
            if (ipstatus != null) {
                addNameserver(nameservers, ipstatus.getDns1());
                addNameserver(nameservers, ipstatus.getDns2());
            }
        }
        return CloudInitNetworkUtil.distinctNameservers(new ArrayList<>(nameservers));
    }

    private Ippool getPrimaryDnsIppool(List<Ippool> ippoolList) {
        if (ippoolList == null || ippoolList.isEmpty()) {
            return null;
        }
        for (Ippool ippool : ippoolList) {
            if (ippool != null && Objects.equals(ippool.getIpVersion(), IP_VERSION_4)) {
                return ippool;
            }
        }
        return ippoolList.get(0);
    }

    private List<String> buildVpcNameservers(List<Subnetpool> subnetpoolList) {
        Set<String> nameservers = new LinkedHashSet<>();
        if (subnetpoolList != null && !subnetpoolList.isEmpty()) {
            addNameserver(nameservers, subnetpoolList.get(0).getDns());
        }
        return CloudInitNetworkUtil.distinctNameservers(new ArrayList<>(nameservers));
    }

    private void addNameserver(Set<String> nameservers, String nameserver) {
        if (StringUtils.isNotBlank(nameserver)) {
            nameservers.add(nameserver.trim());
        }
    }

    private Subnet resolveTargetVpcSubnet(VmhostSnapshot sourceSnapshot, Integer targetNodeId) {
        if (sourceSnapshot.vpcSubnetId != null) {
            Subnet subnet = subnetService.getById(sourceSnapshot.vpcSubnetId);
            if (!isNatIpPoolSubnet(subnet, targetNodeId) && isSubnetBelongToNode(subnet, targetNodeId)) {
                return subnet;
            }
        }
        if (StringUtils.isBlank(sourceSnapshot.bridge)) {
            return null;
        }
        QueryWrapper<Subnet> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("vnet", sourceSnapshot.bridge.trim());
        List<Subnet> subnetList = subnetService.list(queryWrapper);
        if (subnetList == null) {
            return null;
        }
        for (Subnet subnet : subnetList) {
            if (!isNatIpPoolSubnet(subnet, targetNodeId) && isSubnetBelongToNode(subnet, targetNodeId)) {
                return subnet;
            }
        }
        return null;
    }

    private boolean isNatIpPoolSubnet(Subnet subnet, Integer nodeId) {
        if (subnet == null || nodeId == null) {
            return false;
        }
        Master node = masterService.getById(nodeId);
        if (node == null || node.getNatippool() == null || node.getNatippool() <= 0) {
            return false;
        }
        Ipstatus natPool = ipstatusService.getById(node.getNatippool());
        if (natPool == null || !Objects.equals(natPool.getNodeid(), nodeId)) {
            return false;
        }
        return Objects.equals(subnet.getMask(), natPool.getMask())
                && isSameAddress(getSubnetGatewayOrAddress(subnet), natPool.getGateway());
    }

    private String getSubnetGatewayOrAddress(Subnet subnet) {
        if (subnet == null) {
            return null;
        }
        if (StringUtils.isNotBlank(subnet.getGateway())) {
            return subnet.getGateway();
        }
        return subnet.getSubnet();
    }

    private boolean isSameAddress(String left, String right) {
        String normalizedLeft = normalizeAddress(left);
        String normalizedRight = normalizeAddress(right);
        return normalizedLeft != null && normalizedLeft.equals(normalizedRight);
    }

    private String normalizeAddress(String address) {
        if (StringUtils.isBlank(address)) {
            return null;
        }
        String value = address.trim();
        int cidrIndex = value.indexOf('/');
        if (cidrIndex >= 0) {
            value = value.substring(0, cidrIndex).trim();
        }
        return value;
    }

    private boolean isSubnetBelongToNode(Subnet subnet, Integer nodeId) {
        if (subnet == null || subnet.getId() == null || nodeId == null) {
            return false;
        }
        if (subnet.getNodeid() != null) {
            return Objects.equals(subnet.getNodeid(), nodeId);
        }
        QueryWrapper<Subnetpool> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("subnat_id", subnet.getId());
        queryWrapper.eq("node_id", nodeId);
        queryWrapper.last("limit 1");
        return subnetpoolService.getOne(queryWrapper) != null;
    }

    private String resolveClassicBridge(Vmhost vmhost, Master targetNode) {
        if (Objects.equals(vmhost.getIfnat(), 1)) {
            if (!Objects.equals(targetNode.getNaton(), 1)) {
                throw new IllegalStateException("target node has not enabled NAT");
            }
            if (StringUtils.isBlank(targetNode.getNatbridge())) {
                throw new IllegalStateException("target node NAT bridge is empty");
            }
            return targetNode.getNatbridge().trim();
        }
        if (StringUtils.isNotBlank(vmhost.getBridge())) {
            return vmhost.getBridge().trim();
        }
        return "vmbr0";
    }

    private void switchVmhostToTarget(Vmhost vmhost, VmhostSnapshot sourceSnapshot, NetworkMigrationResult result,
                                      Integer targetNodeId, Integer targetVmid, String targetStorage) {
        vmhost.setNodeid(targetNodeId);
        vmhost.setVmid(targetVmid);
        vmhost.setStorage(targetStorage);
        vmhost.setBridge(result.targetBridge);
        if (result.targetVpcSubnetId != null) {
            vmhost.setVpcSubnetId(result.targetVpcSubnetId);
        }
        vmhost.setIpConfig(result.ipConfig);
        vmhost.setIpList(result.ipList);
        vmhost.setIpData(VmUtil.splitIpAddress(new HashMap<>(result.ipConfig)));
        vmhost.setStatus(1);
        if (!vmhostService.update(vmhost, new UpdateWrapper<Vmhost>().eq("id", vmhost.getId())
                .eq("nodeid", sourceSnapshot.nodeid).eq("vmid", sourceSnapshot.vmid))) {
            throw new IllegalStateException("目标虚拟机网络记录保存失败");
        }
        result.dbSwitched = true;
    }

    private void syncTargetCloudInit(Vmhost vmhost, Master targetNode, NetworkMigrationResult result) {
        String token = configService.getToken();
        ProxmoxApiUtil proxmoxApiUtil = newProxmoxApi();
        HashMap<String, String> cookieMap = masterService.getMasterCookieMap(vmhost.getNodeid());
        JSONObject pveVmConfig = proxmoxApiUtil.getVmConfig(targetNode, cookieMap, vmhost.getVmid());
        String oldNet0 = pveVmConfig == null ? vmhost.getNet0() : pveVmConfig.getString("net0");
        String macAddress = CloudInitNetworkUtil.extractMacAddress(oldNet0);
        String desiredNet0 = CloudInitNetworkUtil.ensurePveNet0Config(oldNet0, result.targetBridge, macAddress,
                formatBandwidth(vmhost.getBandwidth()), shouldEnablePveAntiSpoof(vmhost));
        if (StringUtils.isNotBlank(desiredNet0) && !StringUtils.equals(desiredNet0, oldNet0)) {
            proxmoxApiUtil.resetVmConfig(targetNode, cookieMap, vmhost.getVmid(), "net0", desiredNet0);
            vmhost.setNet0(desiredNet0);
            vmhostService.updateById(vmhost);
        }

        String primaryIpConfig = CloudInitNetworkUtil.getPrimaryIpConfig(result.ipConfig);
        if (StringUtils.isBlank(primaryIpConfig)) {
            throw new IllegalStateException("target cloud-init IP config is empty");
        }
        proxmoxApiUtil.resetVmConfig(targetNode, cookieMap, vmhost.getVmid(), "ipconfig0", primaryIpConfig);
        proxmoxApiUtil.deleteVmConfig(targetNode, cookieMap, vmhost.getVmid(), "nameserver");
        if (result.nameservers != null && !result.nameservers.isEmpty()) {
            proxmoxApiUtil.resetVmConfig(targetNode, cookieMap, vmhost.getVmid(), "nameserver", String.join(" ", result.nameservers));
        }

        if (isWindowsVm(vmhost) || CloudInitNetworkUtil.getIpAddressCount(result.ipConfig) <= 1) {
            removeCicustomNetworkConfig(proxmoxApiUtil, targetNode, cookieMap, vmhost, pveVmConfig);
        } else {
            try {
                CloudInitNetworkUtil.uploadSingleNicNetworkSnippet(targetNode, token, vmhost.getVmid(),
                        result.ipConfig, result.nameservers, macAddress);
            } catch (Exception e) {
                throw new IllegalStateException("failed to write target cloud-init network snippet: vmid=" + vmhost.getVmid(), e);
            }
            proxmoxApiUtil.resetVmConfig(targetNode, cookieMap, vmhost.getVmid(), "cicustom",
                    mergeCicustomNetwork(pveVmConfig, vmhost.getVmid()));
        }
        proxmoxApiUtil.resetVmCloudinit(targetNode, cookieMap, vmhost.getVmid());
        syncVmFirewallProtection(proxmoxApiUtil, targetNode, cookieMap, vmhost, CloudInitNetworkUtil.getFirewallCidrList(result.ipConfig));
    }

    private void migrateNetworkForwards(Task task, Map<Object, Object> params, Vmhost vmhost, Master sourceNode, Master targetNode, List<NatRule> oldNatRules,
                                        List<VpcIpBinding> oldVpcBindings, NetworkMigrationResult result, String token) {
        if (result.vpc) {
            migrateVpcIpForwards(task, params, vmhost, oldVpcBindings, result);
            return;
        }
        if (Objects.equals(vmhost.getIfnat(), 1)) {
            migrateNatRules(task, params, vmhost, sourceNode, targetNode, oldNatRules, result, token);
        }
    }

    private void migrateNatRules(Task task, Map<Object, Object> params, Vmhost vmhost, Master sourceNode, Master targetNode, List<NatRule> oldNatRules,
                                 NetworkMigrationResult result, String token) {
        if (oldNatRules.isEmpty()) {
            return;
        }
        if (StringUtils.isBlank(result.primaryIpv4)) {
            throw new IllegalStateException("target NAT VM has no IPv4 address");
        }
        for (NatRule oldRule : oldNatRules) {
            addNatRuleWithPortFallback(task, params, result, vmhost, targetNode, sourceNode, oldRule, result.primaryIpv4);
        }
    }

    private int addNatRuleWithPortFallback(Task task, Map<Object, Object> params, NetworkMigrationResult result,
                                         Vmhost vmhost, Master targetNode, Master sourceNode, NatRule oldRule, String destinationIp) {
        String targetSourceIp = resolveTargetNatSourceIp(oldRule.sourceIp, sourceNode, targetNode);
        JSONObject exported = ClientApiUtil.exportNatRules(targetNode.getHost(), configService.getToken(), targetNode.getControllerPort());
        requireAgentAccepted(exported);
        Set<Integer> occupied = new LinkedHashSet<>();
        JSONArray rules = exported.getJSONObject("data").getJSONArray("port_rules");
        if (rules == null) {
            throw new IllegalStateException("目标NAT规则导出无效");
        }
        for (int i = 0; i < rules.size(); i++) {
            JSONObject rule = rules.getJSONObject(i);
            if (Objects.equals(targetSourceIp, rule.getString("source_ip"))
                    && Objects.equals(oldRule.protocol, rule.getString("protocol"))) {
                occupied.add(rule.getInteger("source_port"));
            }
        }
        int port = oldRule.sourcePort == null ? 0 : oldRule.sourcePort;
        if (!isUsableNatPort(port, targetNode) || occupied.contains(port)) {
            for (port = 1000; port <= 60050 && (!isUsableNatPort(port, targetNode) || occupied.contains(port)); port++) {
                // 只选择空闲端口；连接失败不得对数万个端口重复发起请求。
            }
        }
        if (port > 60050) {
            throw new IllegalStateException("目标节点没有空闲NAT端口");
        }
        NatRule planned = new NatRule(targetSourceIp, port, destinationIp, oldRule.destinationPort, oldRule.protocol);
        result.createdNatRules.add(planned);
        saveNetworkJournal(task, params, result);
        if (!Boolean.TRUE.equals(ClientApiUtil.addPortForward(targetNode.getHost(), configService.getToken(),
                targetNode.getControllerPort(), vmhost.getId(), targetSourceIp, port, destinationIp,
                oldRule.destinationPort, oldRule.protocol))
                || !natForwardSyncService.savePortRule(targetNode.getId(), vmhost.getId(), targetSourceIp, port,
                destinationIp, oldRule.destinationPort, oldRule.protocol)) {
            throw new IllegalStateException("目标NAT规则创建失败，已保留回收清单");
        }
        return port;
    }

    private String resolveTargetNatSourceIp(String oldSourceIp, Master sourceNode, Master targetNode) {
        if (StringUtils.isNotBlank(oldSourceIp) && sourceNode != null && targetNode != null
                && StringUtils.equals(oldSourceIp.trim(), StringUtils.trim(sourceNode.getNataddr()))
                && StringUtils.isNotBlank(targetNode.getNataddr())) {
            return targetNode.getNataddr().trim();
        }
        return targetNode == null ? oldSourceIp : targetNode.getHost();
    }

    private boolean isUsableNatPort(int port, Master node) {
        return port >= 1000 && port <= 60050
                && !Objects.equals(port, node.getPort())
                && !Objects.equals(port, node.getControllerPort())
                && port != 3128
                && port != 5404
                && port != 5405
                && port != 6080
                && !(port >= 5900 && port <= 5999)
                && !(port >= 59000 && port <= 60050);
    }

    private void migrateVpcIpForwards(Task task, Map<Object, Object> params, Vmhost vmhost, List<VpcIpBinding> oldVpcBindings, NetworkMigrationResult result) {
        deactivateVpcBindings(oldVpcBindings);
        int count = Math.min(result.targetVpcPrivateIps.size(), result.targetVpcPublicIps.size());
        for (int i = 0; i < count; i++) {
            String privateIp = result.targetVpcPrivateIps.get(i).getIp();
            String publicIp = result.targetVpcPublicIps.get(i).getIp();
            Master target = masterService.getById(result.targetNodeId);
            VpcForward forward = plannedVpcForward(target, publicIp, privateIp);
            result.createdVpcForwards.add(forward);
            saveNetworkJournal(task, params, result);
            if (!Boolean.TRUE.equals(vmhostService.addVmhostVpcIpForward(vmhost.getId(), publicIp, privateIp))) {
                throw new IllegalStateException("failed to recreate VPC IP forward: publicIp=" + publicIp + ", privateIp=" + privateIp);
            }
        }
    }

    private VpcForward plannedVpcForward(Master target, String publicIp, String privateIp) {
        VpcForward forward = new VpcForward(publicIp, privateIp);
        String mode = StringUtils.defaultIfBlank(target.getPublicIpMode(), "legacy").trim().toLowerCase(Locale.ROOT);
        String iface = StringUtils.trimToNull(target.getPublicIpInterface());
        if ("auto".equals(mode) || ("legacy".equals(mode) && iface != null)) {
            mode = iface == null ? "legacy" : "l2_alias";
        }
        forward.deliveryMode = mode;
        forward.interfaceName = iface;
        if ("l2_alias".equals(mode) || "proxy_arp".equals(mode)) {
            Map<String, Object> body = new HashMap<>();
            body.put("public_ip", publicIp);
            body.put("mode", mode);
            body.put("interface", iface);
            JSONObject inspected = ClientApiUtil.postControllerApi("https://" + target.getHost() + ":"
                    + target.getControllerPort() + "/network/public-ip/check", body, configService.getToken());
            requireAgentAccepted(inspected);
            JSONObject data = inspected.getJSONObject("data");
            if (data == null || !data.containsKey("address_installed") || !data.containsKey("route_installed")) {
                throw new IllegalStateException("无法确认目标公网IP接入状态，停止分配");
            }
            // 在 ensure 之前持久化接入意图，响应丢失时仍能只撤回本次新增的地址/路由。
            forward.addressInstalled = "l2_alias".equals(mode) && !data.getBooleanValue("address_installed") ? 1 : 0;
            forward.routeInstalled = "proxy_arp".equals(mode) && !data.getBooleanValue("route_installed") ? 1 : 0;
        }
        return forward;
    }

    private void deactivateVpcBindings(List<VpcIpBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return;
        }
        for (VpcIpBinding binding : bindings) {
            UpdateWrapper<VpcIpBinding> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", binding.getId());
            updateWrapper.set("status", VPC_BINDING_STATUS_DELETED);
            updateWrapper.set("update_time", System.currentTimeMillis());
            if (!vpcIpBindingService.update(updateWrapper)) {
                throw new IllegalStateException("源VPC绑定状态保存失败");
            }
            binding.setStatus(VPC_BINDING_STATUS_DELETED);
        }
    }

    private void restoreOldVpcBindings(List<VpcIpBinding> oldVpcBindings) {
        if (oldVpcBindings == null || oldVpcBindings.isEmpty()) {
            return;
        }
        for (VpcIpBinding binding : oldVpcBindings) {
            binding.setStatus(VPC_BINDING_STATUS_ACTIVE);
            binding.setUpdateTime(System.currentTimeMillis());
            if (!vpcIpBindingService.updateById(binding)) {
                throw new IllegalStateException("源VPC绑定恢复失败");
            }
        }
    }

    private void syncSecurityGroupAfterMigration(Vmhost vmhost) {
        if (securityGroupBusinessService == null || vmhost == null || vmhost.getId() == null) {
            return;
        }
        List<Integer> boundGroupIds = securityGroupBusinessService.getBoundGroupIds(vmhost.getId());
        if (boundGroupIds == null || boundGroupIds.isEmpty()) {
            return;
        }
        if (!Boolean.TRUE.equals(securityGroupBusinessService.syncVm(vmhost.getId()))) {
            throw new IllegalStateException("failed to sync security group after migration");
        }
    }

    private void invalidateVncCache(Vmhost vmhost) {
        if (vmhost == null || vmhost.getId() == null) {
            return;
        }
        Vncinfo vncinfo = vncinfoService.selectVncinfoByHostId(Long.valueOf(vmhost.getId()));
        if (vncinfo == null || vncinfo.getId() == null) {
            return;
        }
        vncinfo.setVmid(Long.valueOf(vmhost.getVmid()));
        vncinfoService.updateVncinfo(vncinfo);

        QueryWrapper<Vncdata> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("vnc_id", vncinfo.getId());
        queryWrapper.eq("status", 0);
        List<Vncdata> activeList = vncdataService.list(queryWrapper);
        if (activeList == null || activeList.isEmpty()) {
            return;
        }
        for (Vncdata vncdata : activeList) {
            vncdata.setStatus(1);
            vncdataService.updateVncdata(vncdata);
        }
    }

    private void startTargetVm(Master targetNode, Integer targetNodeId, Integer targetVmid) {
        ProxmoxApiUtil proxmoxApiUtil = newProxmoxApi();
        HashMap<String, String> cookieMap = masterService.getMasterCookieMap(targetNodeId);
        if ("running".equals(vmPowerState(proxmoxApiUtil, targetNode, cookieMap, targetVmid))) {
            return;
        }
        JSONObject response = proxmoxApiUtil.postNodeApi(targetNode, cookieMap,
                "/nodes/" + targetNode.getNodeName() + "/qemu/" + targetVmid + "/status/start", new HashMap<>());
        if (response == null || response.get("data") == null) {
            throw new IllegalStateException("PVE未接受目标虚拟机启动请求");
        }
        long deadline = System.currentTimeMillis() + 180_000;
        while (System.currentTimeMillis() < deadline) {
            assertExecutionLock();
            if ("running".equals(vmPowerState(proxmoxApiUtil, targetNode, cookieMap, targetVmid))) {
                return;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("目标虚拟机启动等待已中断", e);
            }
        }
        throw new IllegalStateException("目标虚拟机未确认启动，保留源端资源等待重试");
    }

    private String vmPowerState(ProxmoxApiUtil api, Master node, HashMap<String, String> cookies, Integer vmid) {
        JSONObject response = api.getVmStatus(node, cookies, vmid);
        JSONObject data = response == null ? null : response.getJSONObject("data");
        String state = data == null ? null : data.getString("status");
        if (!"running".equals(state) && !"stopped".equals(state)) {
            throw new IllegalStateException("无法确认目标虚拟机运行状态，保留迁移资源");
        }
        return state;
    }

    protected void requireTargetStopped(Master node, Integer vmid) {
        if (!"stopped".equals(vmPowerState(newProxmoxApi(), node, masterService.getMasterCookieMap(node.getId()), vmid))) {
            throw new IllegalStateException("目标虚拟机正在运行，禁止回收其网络资源");
        }
    }

    private void releaseSourceNetwork(VmhostSnapshot sourceSnapshot, Master sourceNode, List<NatRule> oldNatRules,
                                      List<VpcIpBinding> oldVpcBindings, String token) {
        if (sourceSnapshot.vpc) {
            deleteSourceVpcForwards(sourceSnapshot, sourceNode, oldVpcBindings, token);
            releaseSourceVpcIps(sourceSnapshot, oldVpcBindings);
            return;
        }
        if (Objects.equals(sourceSnapshot.ifnat, 1)) {
            deleteSourceNatRules(sourceSnapshot, sourceNode, oldNatRules, token);
        }
        ippoolService.releaseIppoolByNodeIdAndVmId(sourceSnapshot.nodeid, sourceSnapshot.vmid, sourceSnapshot.ipList);
    }

    private void deleteSourceNatRules(VmhostSnapshot sourceSnapshot, Master sourceNode, List<NatRule> oldNatRules, String token) {
        for (NatRule rule : oldNatRules) {
            if (!Boolean.TRUE.equals(requestDeletePortForward(sourceNode, token, sourceSnapshot.hostId,
                    rule.sourceIp, rule.sourcePort, rule.destinationIp, rule.destinationPort, rule.protocol))
                    || !natForwardSyncService.deletePortRule(sourceNode.getId(), rule.sourceIp, rule.sourcePort, rule.protocol)) {
                throw new IllegalStateException("源NAT规则删除未确认，保留源IP等待重试");
            }
        }
    }

    private void deleteSourceVpcForwards(VmhostSnapshot sourceSnapshot, Master sourceNode, List<VpcIpBinding> oldVpcBindings, String token) {
        for (VpcIpBinding binding : oldVpcBindings) {
            if (!Boolean.TRUE.equals(requestDeleteIpForward(sourceNode, token, sourceSnapshot.hostId,
                    binding.getPublicIp(), binding.getPrivateIp()))
                    || !natForwardSyncService.deleteIpForwardRule(sourceNode.getId(), binding.getPublicIp())) {
                throw new IllegalStateException("源VPC规则删除未确认，保留源IP等待重试");
            }
            releaseVpcAlias(sourceNode, token, binding.getPublicIp(), binding.getDeliveryMode(), binding.getInterfaceName(),
                    binding.getAddressInstalled(), binding.getRouteInstalled());
        }
    }

    protected Boolean requestDeletePortForward(Master node, String token, Integer hostId, String sourceIp,
                                                Integer sourcePort, String destinationIp, Integer destinationPort, String protocol) {
        return ClientApiUtil.deletePortForward(node.getHost(), token, node.getControllerPort(), hostId,
                sourceIp, sourcePort, destinationIp, destinationPort, protocol);
    }

    protected Boolean requestDeleteIpForward(Master node, String token, Integer hostId, String publicIp, String privateIp) {
        return ClientApiUtil.deleteIpForward(node.getHost(), token, node.getControllerPort(), hostId, publicIp, privateIp);
    }

    private void releaseVpcAlias(Master node, String token, String publicIp, String mode, String iface,
                                  Integer addressInstalled, Integer routeInstalled) {
        if (Integer.valueOf(1).equals(addressInstalled) || Integer.valueOf(1).equals(routeInstalled)) {
            requireAgentAccepted(ClientApiUtil.releaseVpcPublicIp(node.getHost(), token, node.getControllerPort(), publicIp,
                    mode, iface, addressInstalled, routeInstalled));
        }
    }

    private void releaseSourceVpcIps(VmhostSnapshot sourceSnapshot, List<VpcIpBinding> oldVpcBindings) {
        for (String privateIp : sourceSnapshot.ipList) {
            QueryWrapper<Subnetpool> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("node_id", sourceSnapshot.nodeid);
            queryWrapper.eq("subnat_id", sourceSnapshot.vpcSubnetId);
            queryWrapper.eq("ip", privateIp);
            queryWrapper.eq("vm_id", sourceSnapshot.vmid);
            queryWrapper.eq("status", 1);
            queryWrapper.last("limit 1");
            Subnetpool subnetpool = subnetpoolService.getOne(queryWrapper);
            if (subnetpool != null) {
                releaseSubnetpool(subnetpool);
            }
        }
        List<String> publicIpList = new ArrayList<>();
        for (VpcIpBinding binding : oldVpcBindings) {
            if (StringUtils.isNotBlank(binding.getPublicIp())) {
                publicIpList.add(binding.getPublicIp());
            }
        }
        if (publicIpList.isEmpty()) {
            publicIpList = fetchVpcPublicIpFallback(sourceSnapshot);
        }
        ippoolService.releaseIppoolByNodeIdAndVmId(sourceSnapshot.nodeid, sourceSnapshot.vmid, publicIpList);
    }

    private void rollbackTargetNetwork(Vmhost vmhost, VmhostSnapshot sourceSnapshot, NetworkMigrationResult result,
                                       List<VpcIpBinding> oldVpcBindings) {
        if (result == null) {
            return;
        }
        Master target = masterService.getById(result.targetNodeId);
        if (target == null) {
            throw new IllegalStateException("目标节点不存在，保留回收清单");
        }
        requireTargetStopped(target, result.targetVmid);
        String token = configService.getToken();
        // 先确认所有外部规则已删除，任一失败都不释放任何地址。
        for (VpcForward forward : result.createdVpcForwards) {
            if (!Boolean.TRUE.equals(requestDeleteIpForward(target, token, vmhost.getId(), forward.publicIp, forward.privateIp))
                    || !natForwardSyncService.deleteIpForwardRule(target.getId(), forward.publicIp)) {
                throw new IllegalStateException("目标VPC转发回收失败，保留IP与回收清单");
            }
            releaseVpcAlias(target, token, forward.publicIp, forward.deliveryMode, forward.interfaceName,
                    forward.addressInstalled, forward.routeInstalled);
        }
        for (NatRule rule : result.createdNatRules) {
            if (!Boolean.TRUE.equals(requestDeletePortForward(target, token, vmhost.getId(), rule.sourceIp,
                    rule.sourcePort, rule.destinationIp, rule.destinationPort, rule.protocol))
                    || !natForwardSyncService.deletePortRule(target.getId(), rule.sourceIp, rule.sourcePort, rule.protocol)) {
                throw new IllegalStateException("目标NAT转发回收失败，保留IP与回收清单");
            }
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            for (VpcForward forward : result.createdVpcForwards) {
                UpdateWrapper<VpcIpBinding> update = new UpdateWrapper<VpcIpBinding>()
                        .eq("host_id", vmhost.getId()).eq("node_id", target.getId())
                        .eq("public_ip", forward.publicIp).eq("private_ip", forward.privateIp)
                        .set("status", VPC_BINDING_STATUS_DELETED).set("update_time", System.currentTimeMillis());
                vpcIpBindingService.update(update);
                if (vpcIpBindingService.count(new QueryWrapper<VpcIpBinding>()
                        .eq("host_id", vmhost.getId()).eq("node_id", target.getId())
                        .eq("public_ip", forward.publicIp).eq("private_ip", forward.privateIp)
                        .eq("status", VPC_BINDING_STATUS_ACTIVE)) != 0) {
                    throw new IllegalStateException("目标VPC绑定清理未确认，保留IP");
                }
            }
            for (Ippool ippool : result.targetIppools) {
                releaseIppool(ippool);
            }
            for (Ippool ippool : result.targetVpcPublicIps) {
                releaseIppool(ippool);
            }
            for (Subnetpool subnetpool : result.targetVpcPrivateIps) {
                releaseSubnetpool(subnetpool);
            }
            sourceSnapshot.restore(vmhost);
            if (!vmhostService.update(vmhost, new UpdateWrapper<Vmhost>().eq("id", vmhost.getId())
                    .and(location -> location.eq("nodeid", sourceSnapshot.nodeid).eq("vmid", sourceSnapshot.vmid)
                            .or(targetLocation -> targetLocation.eq("nodeid", result.targetNodeId).eq("vmid", result.targetVmid))))) {
                throw new IllegalStateException("源虚拟机网络记录恢复失败");
            }
            restoreOldVpcBindings(oldVpcBindings);
        });
    }

    private void releaseIppool(Ippool ippool) {
        if (ippool == null || ippool.getId() == null) {
            return;
        }
        if (!ippoolService.releaseBoundIppool(ippool.getId(), ippool.getVmId())) {
            Ippool current = ippoolService.getById(ippool.getId());
            if (current == null || !Integer.valueOf(0).equals(current.getStatus())) {
                throw new IllegalStateException("迁移IP释放未确认: " + ippool.getIp());
            }
        }
    }

    private void releaseSubnetpool(Subnetpool subnetpool) {
        if (subnetpool == null || subnetpool.getId() == null) {
            return;
        }
        UpdateWrapper<Subnetpool> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", subnetpool.getId());
        updateWrapper.eq("vm_id", subnetpool.getVmId());
        updateWrapper.eq("status", 1);
        updateWrapper.set("status", 0);
        updateWrapper.set("vm_id", 0);
        updateWrapper.set("mac", null);
        if (!subnetpoolService.update(updateWrapper)) {
            Subnetpool current = subnetpoolService.getById(subnetpool.getId());
            if (current == null || !Integer.valueOf(0).equals(current.getStatus())) {
                throw new IllegalStateException("迁移私网IP释放未确认: " + subnetpool.getIp());
            }
        }
    }

    private void removeCicustomNetworkConfig(ProxmoxApiUtil proxmoxApiUtil, Master node, HashMap<String, String> cookieMap,
                                             Vmhost vmhost, JSONObject pveVmConfig) {
        if (pveVmConfig == null || StringUtils.isBlank(pveVmConfig.getString("cicustom"))) {
            return;
        }
        String cicustom = pveVmConfig.getString("cicustom");
        String value = removeCicustomNetwork(cicustom);
        if (StringUtils.equals(cicustom, value)) {
            return;
        }
        if (StringUtils.isBlank(value)) {
            proxmoxApiUtil.deleteVmConfig(node, cookieMap, vmhost.getVmid(), "cicustom");
        } else {
            proxmoxApiUtil.resetVmConfig(node, cookieMap, vmhost.getVmid(), "cicustom", value);
        }
    }

    private String mergeCicustomNetwork(JSONObject pveVmConfig, Integer vmid) {
        String networkValue = "network=" + CloudInitNetworkUtil.getNetworkSnippetVolume(vmid);
        if (pveVmConfig == null || StringUtils.isBlank(pveVmConfig.getString("cicustom"))) {
            return networkValue;
        }
        String oldCicustom = removeCicustomNetwork(pveVmConfig.getString("cicustom"));
        if (StringUtils.isBlank(oldCicustom)) {
            return networkValue;
        }
        return oldCicustom + "," + networkValue;
    }

    private String removeCicustomNetwork(String cicustom) {
        if (StringUtils.isBlank(cicustom)) {
            return cicustom;
        }
        List<String> parts = new ArrayList<>();
        for (String item : cicustom.split(",")) {
            String value = item.trim();
            if (StringUtils.isBlank(value) || value.startsWith("network=")) {
                continue;
            }
            parts.add(value);
        }
        return String.join(",", parts);
    }

    private void syncVmFirewallProtection(ProxmoxApiUtil proxmoxApiUtil, Master node, HashMap<String, String> cookieMap,
                                          Vmhost vmhost, Collection<String> allowedIps) {
        boolean antiSpoofEnabled = shouldEnablePveAntiSpoof(vmhost);
        if (antiSpoofEnabled) {
            proxmoxApiUtil.ensureFirewallEnabledAccept(node, cookieMap);
            proxmoxApiUtil.enableVmFirewallAntiSpoof(node, cookieMap, vmhost.getVmid());
            proxmoxApiUtil.createVmFirewallIpset(node, cookieMap, vmhost.getVmid(), "ipfilter-net0");
        }
        JSONObject ipsetEntries = proxmoxApiUtil.getVmFirewallIpsetEntries(node, cookieMap, vmhost.getVmid(), "ipfilter-net0");
        LinkedHashSet<String> currentCidrSet = new LinkedHashSet<>();
        if (ipsetEntries != null && ipsetEntries.getJSONArray("data") != null) {
            JSONArray data = ipsetEntries.getJSONArray("data");
            for (int i = 0; i < data.size(); i++) {
                JSONObject item = data.getJSONObject(i);
                if (item != null && StringUtils.isNotBlank(item.getString("cidr"))) {
                    currentCidrSet.add(item.getString("cidr").trim());
                }
            }
        }
        LinkedHashSet<String> desiredCidrSet = new LinkedHashSet<>();
        if (allowedIps != null) {
            desiredCidrSet.addAll(CloudInitNetworkUtil.normalizeFirewallCidrs(allowedIps));
        }
        for (String cidr : currentCidrSet) {
            if (!antiSpoofEnabled || !desiredCidrSet.contains(cidr)) {
                proxmoxApiUtil.deleteVmFirewallIpsetEntry(node, cookieMap, vmhost.getVmid(), "ipfilter-net0", cidr);
            }
        }
        if (!antiSpoofEnabled) {
            proxmoxApiUtil.deleteVmFirewallIpset(node, cookieMap, vmhost.getVmid(), "ipfilter-net0");
            proxmoxApiUtil.disableVmFirewallFilters(node, cookieMap, vmhost.getVmid());
            return;
        }
        for (String cidr : desiredCidrSet) {
            if (!currentCidrSet.contains(cidr)) {
                proxmoxApiUtil.addVmFirewallIpsetEntry(node, cookieMap, vmhost.getVmid(), "ipfilter-net0", cidr);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private VmhostSnapshot restoreSnapshot(Map<Object, Object> params) {
        Object value = params.get("sourceSnapshot");
        if (!(value instanceof Map)) {
            return null;
        }
        return VmhostSnapshot.fromMap((Map<Object, Object>) value);
    }

    private List<NatRule> fetchNatRules(VmhostSnapshot snapshot) {
        if (snapshot == null || !Objects.equals(snapshot.ifnat, 1) || snapshot.vpc) {
            return Collections.emptyList();
        }
        return fetchNatRulesById(snapshot.hostId);
    }

    private List<VpcIpBinding> fetchActiveVpcBindings(VmhostSnapshot snapshot) {
        if (snapshot == null || !snapshot.vpc || snapshot.hostId == null) {
            return Collections.emptyList();
        }
        return fetchActiveVpcBindingsById(snapshot.hostId);
    }

    private NatRule toNatRule(Object item) {
        JSONObject jsonObject;
        if (item instanceof JSONObject object) {
            jsonObject = object;
        } else if (item instanceof Map<?, ?> map) {
            jsonObject = new JSONObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                jsonObject.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        } else {
            return null;
        }
        Integer sourcePort = jsonObject.getInteger("source_port");
        Integer destinationPort = jsonObject.getInteger("destination_port");
        String protocol = StringUtils.defaultIfBlank(jsonObject.getString("protocol"), "tcp").toLowerCase(Locale.ROOT);
        if (sourcePort == null || destinationPort == null) {
            return null;
        }
        return new NatRule(jsonObject.getString("source_ip"), sourcePort, jsonObject.getString("destination_ip"),
                destinationPort, protocol);
    }

    private List<NatRule> fetchNatRulesById(Integer hostId) {
        if (hostId == null) {
            return Collections.emptyList();
        }
        Object response = vmhostService.getVmhostNatByVmid(1, 10000, hostId);
        if (!(response instanceof ResponseResult<?> result) || result.getData() == null) {
            return Collections.emptyList();
        }
        List<NatRule> rules = new ArrayList<>();
        Object data = result.getData();
        if (data instanceof JSONArray array) {
            for (int i = 0; i < array.size(); i++) {
                NatRule rule = toNatRule(array.get(i));
                if (rule != null) {
                    rules.add(rule);
                }
            }
        } else if (data instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                NatRule rule = toNatRule(item);
                if (rule != null) {
                    rules.add(rule);
                }
            }
        }
        return rules;
    }

    private List<VpcIpBinding> fetchActiveVpcBindingsById(Integer hostId) {
        QueryWrapper<VpcIpBinding> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("host_id", hostId);
        queryWrapper.eq("status", VPC_BINDING_STATUS_ACTIVE);
        queryWrapper.orderByAsc("id");
        List<VpcIpBinding> bindings = vpcIpBindingService.list(queryWrapper);
        return bindings == null ? Collections.emptyList() : bindings;
    }

    private List<String> fetchVpcPublicIpFallback(VmhostSnapshot sourceSnapshot) {
        if (!sourceSnapshot.vpc || sourceSnapshot.nodeid == null || sourceSnapshot.vmid == null) {
            return Collections.emptyList();
        }
        QueryWrapper<Ippool> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("node_id", sourceSnapshot.nodeid);
        queryWrapper.eq("vm_id", sourceSnapshot.vmid);
        queryWrapper.eq("status", 1);
        if (sourceSnapshot.ipList != null && !sourceSnapshot.ipList.isEmpty()) {
            queryWrapper.notIn("ip", sourceSnapshot.ipList);
        }
        queryWrapper.orderByAsc("id");
        List<Ippool> ippoolList = ippoolService.list(queryWrapper);
        if (ippoolList == null || ippoolList.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ipList = new ArrayList<>();
        for (Ippool ippool : ippoolList) {
            ipList.add(ippool.getIp());
        }
        return ipList;
    }

    private boolean isVpcNetwork(Vmhost vmhost) {
        return vmhost != null && NETWORK_TYPE_VPC.equalsIgnoreCase(vmhost.getNetworkType());
    }

    private boolean shouldEnablePveAntiSpoof(Vmhost vmhost) {
        return vmhost != null && (isVpcNetwork(vmhost) || !Objects.equals(vmhost.getIfnat(), 1));
    }

    private boolean isWindowsVm(Vmhost vmhost) {
        return vmhost != null && "windows".equalsIgnoreCase(vmhost.getOsType());
    }

    private void createApplyWindowsVmIpTaskIfNeeded(Vmhost vmhost) {
        if (!isWindowsVm(vmhost) || CloudInitNetworkUtil.getIpConfigEntryCount(vmhost.getIpConfig()) <= 1
                || getPendingApplyWindowsVmIpTask(vmhost.getId()) != null) {
            return;
        }
        Task task = new Task();
        task.setNodeid(vmhost.getNodeid());
        task.setVmid(vmhost.getVmid());
        task.setHostid(vmhost.getId());
        task.setType(TaskType.APPLY_WINDOWS_VM_IP);
        task.setStatus(0);
        HashMap<Object, Object> params = new HashMap<>();
        params.put("source", "windows_ip_sync_migration");
        params.put("retryCount", 0);
        params.put("maxRetry", 30);
        params.put("retryDelaySeconds", 10);
        task.setParams(params);
        task.setCreateDate(System.currentTimeMillis());
        if (!taskService.insertTask(task)) {
            throw new IllegalStateException("failed to create Windows IP sync task: hostId=" + vmhost.getId());
        }
        vmhostService.addVmHostTask(vmhost.getId(), task.getId());
    }

    private Task getPendingApplyWindowsVmIpTask(Integer hostId) {
        if (hostId == null) {
            return null;
        }
        QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("hostid", hostId);
        queryWrapper.eq("type", TaskType.APPLY_WINDOWS_VM_IP);
        queryWrapper.in("status", 0, 1);
        queryWrapper.orderByDesc("create_date");
        queryWrapper.last("limit 1");
        return taskService.getOne(queryWrapper);
    }

    private String formatBandwidth(Integer bandwidth) {
        if (bandwidth == null) {
            return null;
        }
        return String.format(Locale.US, "%.2f", bandwidth / 8.0);
    }

    private JSONObject waitAgentStage(Task task, Master node, String token, String taskId, String successStage,
                                      int minProgress, int maxProgress) throws Exception {
        int lastProgress = -1;
        long lastProgressTime = System.currentTimeMillis();
        long deadline = System.currentTimeMillis() + 24L * 60L * 60L * 1000L;
        long nextChannelRenewal = 0;
        while (System.currentTimeMillis() < deadline) {
            assertExecutionLock();
            Map<Object, Object> params = ensureParams(task);
            String channel = stringValue(params.get("sourceTransferChannel"));
            if (channel != null && System.currentTimeMillis() >= nextChannelRenewal) {
                Master source = masterService.getById(toInteger(params.get("sourceNodeId")));
                requireAgentAccepted(requestMigrationChannel(source, token, taskId, "renew", channel));
                nextChannelRenewal = System.currentTimeMillis() + 60_000;
            }
            JSONObject result = requestMigrationStatus(node, token, taskId);
            JSONObject data = result == null ? null : result.getJSONObject("data");
            if (data == null) {
                throw new IllegalStateException("failed to get migration status");
            }
            String status = data.getString("status");
            String stage = data.getString("stage");
            Integer agentProgress = data.getInteger("progress");
            int progress = minProgress;
            if (agentProgress != null) {
                progress = minProgress + Math.max(0, Math.min(100, agentProgress)) * (maxProgress - minProgress) / 100;
            }
            updateStageProgress(task, stage, progress);
            if (progress != lastProgress) {
                lastProgress = progress;
                lastProgressTime = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - lastProgressTime > 60L * 60L * 1000L) {
                throw new IllegalStateException("migration stage timeout: progress did not change for 1 hour, stage=" + stage);
            }
            if ("FAILED".equals(status)) {
                throw new IllegalStateException(data.getString("error"));
            }
            if ("SUCCESS".equals(status) && successStage.equals(stage)) {
                return data;
            }
            Thread.sleep(2000);
        }
        throw new IllegalStateException("migration stage timeout: " + successStage);
    }

    private void failTask(Task task, String error) {
        Map<Object, Object> params = ensureParams(task);
        params.put("stage", "FAILED");
        params.put("progress", 0);
        params.put("error", error);
        params.put("updateTime", System.currentTimeMillis());
        appendLog(task, params, "迁移失败: " + error);
        task.setParams(params);
        task.setStatus(3);
        task.setError(error);
        persistTask(task);
        UnifiedLogger.error(UnifiedLogger.LogType.TASK_MIGRATE_VM, "VM migration failed, taskId=" + task.getId() + ", error=" + error);
    }

    private void updateProgress(Task task, String stage, int progress, String error, String message) {
        Map<Object, Object> params = ensureParams(task);
        params.put("stage", stage);
        params.put("progress", progress);
        params.put("updateTime", System.currentTimeMillis());
        if (error != null) {
            params.put("error", error);
        }
        appendLog(task, params, message);
        task.setParams(params);
        persistTask(task);
    }

    /**
     * 被控端上报的细粒度进度只更新进度条，避免每个百分比都追加一条日志
     */
    private void updateStageProgress(Task task, String stage, int progress) {
        Map<Object, Object> params = ensureParams(task);
        params.put("progress", progress);
        params.put("updateTime", System.currentTimeMillis());
        task.setParams(params);
        persistTask(task);
    }

    public void appendLog(Task task, Map<Object, Object> params, String message) {
        vmMigrationService.appendTaskLog(task, params, message);
    }

    private Map<Object, Object> ensureParams(Task task) {
        if (task.getParams() == null) {
            task.setParams(new HashMap<>());
        }
        return task.getParams();
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private String resolveTargetStorage(String targetStorage, Master targetNode) {
        String normalizedStorage = stringValue(targetStorage);
        if (normalizedStorage != null && !"auto".equalsIgnoreCase(normalizedStorage)) {
            return normalizedStorage;
        }
        String nodeStorage = targetNode == null ? null : stringValue(targetNode.getAutoStorage());
        if (nodeStorage != null && !"auto".equalsIgnoreCase(nodeStorage)) {
            return nodeStorage;
        }
        return DEFAULT_CREATE_VM_STORAGE;
    }

    private String getFileName(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalStateException("migration backup file does not exist");
        }
        String normalizedPath = path.replace("\\", "/");
        int index = normalizedPath.lastIndexOf("/");
        return index >= 0 ? normalizedPath.substring(index + 1) : normalizedPath;
    }

    private static class NetworkMigrationResult {
        private boolean vpc;
        private boolean dbSwitched;
        private Integer targetNodeId;
        private Integer targetVmid;
        private Integer targetVpcSubnetId;
        private String targetBridge;
        private Map<String, String> ipConfig = new LinkedHashMap<>();
        private List<String> ipList = new ArrayList<>();
        private List<String> nameservers = new ArrayList<>();
        private String primaryIpv4;
        private List<Ippool> targetIppools = new ArrayList<>();
        private List<Subnetpool> targetVpcPrivateIps = new ArrayList<>();
        private List<Ippool> targetVpcPublicIps = new ArrayList<>();
        private List<NatRule> createdNatRules = new ArrayList<>();
        private List<VpcForward> createdVpcForwards = new ArrayList<>();
    }

    private static class VmhostSnapshot {
        private Integer hostId;
        private Integer nodeid;
        private Integer vmid;
        private String storage;
        private Integer status;
        private String bridge;
        private String net0;
        private String networkType;
        private Integer vpcSubnetId;
        private Integer ifnat;
        private boolean vpc;
        private Map<String, String> ipConfig = new LinkedHashMap<>();
        private List<String> ipList = new ArrayList<>();

        private static VmhostSnapshot of(Vmhost vmhost) {
            VmhostSnapshot snapshot = new VmhostSnapshot();
            snapshot.hostId = vmhost.getId();
            snapshot.nodeid = vmhost.getNodeid();
            snapshot.vmid = vmhost.getVmid();
            snapshot.storage = vmhost.getStorage();
            snapshot.status = vmhost.getStatus();
            snapshot.bridge = vmhost.getBridge();
            snapshot.net0 = vmhost.getNet0();
            snapshot.networkType = vmhost.getNetworkType();
            snapshot.vpcSubnetId = vmhost.getVpcSubnetId();
            snapshot.ifnat = vmhost.getIfnat();
            snapshot.vpc = NETWORK_TYPE_VPC.equalsIgnoreCase(vmhost.getNetworkType());
            if (vmhost.getIpConfig() != null) {
                snapshot.ipConfig.putAll(vmhost.getIpConfig());
            }
            if (vmhost.getIpList() != null) {
                snapshot.ipList.addAll(vmhost.getIpList());
            }
            if (snapshot.ipList.isEmpty() && !snapshot.ipConfig.isEmpty()) {
                snapshot.ipList.addAll(CloudInitNetworkUtil.getIpList(snapshot.ipConfig));
            }
            return snapshot;
        }

        /**
         * 快照随任务参数落库。任务失败多步后重启主控、或数据库已被切到目标端时，
         * 只有快照能还原源端信息，续跑和回滚都依赖它。
         */
        private Map<Object, Object> toMap() {
            Map<Object, Object> map = new LinkedHashMap<>();
            map.put("hostId", hostId);
            map.put("nodeid", nodeid);
            map.put("vmid", vmid);
            map.put("storage", storage);
            map.put("status", status);
            map.put("bridge", bridge);
            map.put("net0", net0);
            map.put("networkType", networkType);
            map.put("vpcSubnetId", vpcSubnetId);
            map.put("ifnat", ifnat);
            map.put("vpc", vpc);
            map.put("ipConfig", new LinkedHashMap<>(ipConfig));
            map.put("ipList", new ArrayList<>(ipList));
            return map;
        }

        private static VmhostSnapshot fromMap(Map<Object, Object> map) {
            if (map == null || map.isEmpty()) {
                return null;
            }
            VmhostSnapshot snapshot = new VmhostSnapshot();
            snapshot.hostId = toIntegerValue(map.get("hostId"));
            snapshot.nodeid = toIntegerValue(map.get("nodeid"));
            snapshot.vmid = toIntegerValue(map.get("vmid"));
            snapshot.storage = stringValue(map.get("storage"));
            snapshot.status = toIntegerValue(map.get("status"));
            snapshot.bridge = stringValue(map.get("bridge"));
            snapshot.net0 = stringValue(map.get("net0"));
            snapshot.networkType = stringValue(map.get("networkType"));
            snapshot.vpcSubnetId = toIntegerValue(map.get("vpcSubnetId"));
            snapshot.ifnat = toIntegerValue(map.get("ifnat"));
            snapshot.vpc = Boolean.TRUE.equals(map.get("vpc"));
            Object ipConfigValue = map.get("ipConfig");
            if (ipConfigValue instanceof Map) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) ipConfigValue).entrySet()) {
                    snapshot.ipConfig.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
            Object ipListValue = map.get("ipList");
            if (ipListValue instanceof List) {
                for (Object ip : (List<?>) ipListValue) {
                    if (ip != null) {
                        snapshot.ipList.add(String.valueOf(ip));
                    }
                }
            }
            return snapshot;
        }

        private void restore(Vmhost vmhost) {
            vmhost.setNodeid(nodeid);
            vmhost.setVmid(vmid);
            vmhost.setStorage(storage);
            vmhost.setStatus(status);
            vmhost.setBridge(bridge);
            vmhost.setNet0(net0);
            vmhost.setNetworkType(networkType);
            vmhost.setVpcSubnetId(vpcSubnetId);
            vmhost.setIpConfig(ipConfig);
            vmhost.setIpList(ipList);
            vmhost.setIpData(VmUtil.splitIpAddress(new HashMap<>(ipConfig)));
        }
    }

    private static Integer toIntegerValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String result = String.valueOf(value).trim();
        return result.isEmpty() ? null : result;
    }

    private static class NatRule {
        private String sourceIp;
        private Integer sourcePort;
        private String destinationIp;
        private Integer destinationPort;
        private String protocol;

        private NatRule() {
        }

        private NatRule(String sourceIp, Integer sourcePort, String destinationIp, Integer destinationPort, String protocol) {
            this.sourceIp = sourceIp;
            this.sourcePort = sourcePort;
            this.destinationIp = destinationIp;
            this.destinationPort = destinationPort;
            this.protocol = protocol;
        }
    }

    private static class VpcForward {
        private String publicIp;
        private String privateIp;
        private String deliveryMode;
        private String interfaceName;
        private Integer addressInstalled;
        private Integer routeInstalled;

        private VpcForward() {
        }

        private VpcForward(String publicIp, String privateIp) {
            this.publicIp = publicIp;
            this.privateIp = privateIp;
        }
    }
}
