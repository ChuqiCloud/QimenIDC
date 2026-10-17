package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
import com.chuqiyun.proxmoxveams.constant.TaskType;
import com.chuqiyun.proxmoxveams.dto.VmMigrationBatchDto;
import com.chuqiyun.proxmoxveams.dto.VmMigrationLogView;
import com.chuqiyun.proxmoxveams.dto.VmMigrationParams;
import com.chuqiyun.proxmoxveams.dto.VmMigrationTaskView;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.security.TaskPasswordValues;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmMigrationService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 迁移任务的通用能力：入队、查询、日志与断点续跑。
 * 真正的执行流程在 {@link com.chuqiyun.proxmoxveams.cron.VmMigrationCron}。
 */
@Service
public class VmMigrationServiceImpl implements VmMigrationService {
    public static final String DEFAULT_CREATE_VM_STORAGE = "local-lvm";

    /**
     * 阶段顺序即断点续跑的推进顺序，重试时从失败阶段的下一步开始
     */
    public static final List<String> STAGES = Collections.unmodifiableList(Arrays.asList(
            "PENDING", "BACKUP", "RESTORE", "NETWORK_REBIND", "START_TARGET_VM", "CLEANUP_SOURCE", "RELEASE_SOURCE_NETWORK", "DONE"));

    public static final Map<String, String> STAGE_TEXT;

    static {
        Map<String, String> text = new LinkedHashMap<>();
        text.put("PENDING", "排队中");
        text.put("BACKUP", "源节点打包");
        text.put("RESTORE", "目标节点导入");
        text.put("NETWORK_REBIND", "网络与端口转发重建");
        text.put("START_TARGET_VM", "启动目标虚拟机");
        text.put("CLEANUP_SOURCE", "清理源虚拟机");
        text.put("RELEASE_SOURCE_NETWORK", "回收源端网络资源");
        text.put("DONE", "已完成");
        STAGE_TEXT = Collections.unmodifiableMap(text);
    }

    /**
     * 失败后可以直接续跑的阶段：打包/导入/网络重建都可在原资源配置上继续
     */
    private static final List<String> RESUMABLE_STAGES = Arrays.asList("BACKUP", "RESTORE", "NETWORK_REBIND", "START_TARGET_VM");

    @Resource
    private TaskService taskService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private MasterService masterService;
    @Resource
    private ConfigService configService;

    @Override
    public synchronized VmMigrationBatchDto submit(VmMigrationParams params) {
        VmMigrationBatchDto result = new VmMigrationBatchDto();
        String batchId = "mig-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        result.setBatchId(batchId);
        if (params == null) {
            return result;
        }
        result.setTargetNodeId(params.getTargetNodeId());
        List<Integer> hostIds = resolveHostIds(params);
        if (hostIds.isEmpty()) {
            VmMigrationBatchDto.Item item = VmMigrationBatchDto.Item.rejected(params.getHostId(), "hostIds不能为空");
            result.getItems().add(item);
            result.setRejectedCount(1);
            return result;
        }
        Master targetNode = params.getTargetNodeId() == null ? null : masterService.getById(params.getTargetNodeId());
        if (targetNode == null) {
            for (Integer hostId : hostIds) {
                result.getItems().add(VmMigrationBatchDto.Item.rejected(hostId, "目标宿主机不存在"));
            }
            result.setRejectedCount(hostIds.size());
            return result;
        }
        result.setTargetStorage(resolveTargetStorage(params.getTargetStorage(), targetNode));

        List<String> runningStages = new ArrayList<>();
        Set<Integer> reservedTargetVmids = reservedTargetVmids(targetNode.getId());
        for (Integer hostId : hostIds) {
            VmMigrationBatchDto.Item item = submitOne(params, targetNode, batchId, hostId, runningStages, reservedTargetVmids);
            result.getItems().add(item);
            if ("QUEUED".equals(item.getStatus())) {
                result.setAcceptedCount(result.getAcceptedCount() + 1);
            } else {
                result.setRejectedCount(result.getRejectedCount() + 1);
            }
        }
        UnifiedLogger.log(UnifiedLogger.LogType.TASK_MIGRATE_VM,
                "批量迁移受理, batchId={}, accepted={}, rejected={}", batchId, result.getAcceptedCount(), result.getRejectedCount());
        return result;
    }

    private VmMigrationBatchDto.Item submitOne(VmMigrationParams params, Master targetNode, String batchId, Integer hostId,
                                               List<String> runningStages, Set<Integer> reservedTargetVmids) {
        Vmhost vmhost = hostId == null ? null : vmhostService.getById(hostId);
        if (vmhost == null) {
            return VmMigrationBatchDto.Item.rejected(hostId, "虚拟机不存在");
        }
        String reason = checkMigratable(vmhost, targetNode, runningStages);
        if (reason != null) {
            return VmMigrationBatchDto.Item.rejected(hostId, reason);
        }
        Integer targetVmid = params.getHostIds() == null || params.getHostIds().isEmpty() ? params.getTargetVmid() : null;
        if (targetVmid == null) {
            targetVmid = vmhostService.getNewVmid(targetNode.getId());
        }
        if (targetVmid == null || targetVmid <= 0) {
            return VmMigrationBatchDto.Item.rejected(hostId, "targetVmid自动分配失败");
        }
        // 分配器只看 PVE 与虚拟机表，尚未导入的迁移任务也必须预留 VMID，避免覆盖同批镜像。
        if (reservedTargetVmids.contains(targetVmid)) {
            return VmMigrationBatchDto.Item.rejected(hostId, "目标VMID已被其他迁移任务预留，请稍后重试或指定其他VMID");
        }

        Map<Object, Object> taskParams = new HashMap<>();
        taskParams.put("batchId", batchId);
        taskParams.put("batchSize", params.getHostIds() == null ? 1 : params.getHostIds().size());
        taskParams.put("sourceNodeId", vmhost.getNodeid());
        taskParams.put("targetNodeId", targetNode.getId());
        taskParams.put("sourceVmid", vmhost.getVmid());
        taskParams.put("targetVmid", targetVmid);
        taskParams.put("targetStorage", resolveTargetStorage(params.getTargetStorage(), targetNode));
        taskParams.put("backupDir", params.getBackupDir());
        taskParams.put("startAfterMigration", Boolean.TRUE.equals(params.getStartAfterMigration()));
        taskParams.put("stage", "PENDING");
        taskParams.put("progress", 0);
        taskParams.put("retryCount", 0);
        taskParams.put("logLines", new ArrayList<String>());

        Task task = new Task();
        task.setNodeid(vmhost.getNodeid());
        task.setHostid(vmhost.getId());
        task.setVmid(vmhost.getVmid());
        task.setType(TaskType.MIGRATE_VM);
        task.setStatus(0);
        task.setCreateDate(System.currentTimeMillis());
        task.setParams(taskParams);
        if (!taskService.insertTask(task)) {
            return VmMigrationBatchDto.Item.rejected(hostId, "创建迁移任务失败");
        }
        reservedTargetVmids.add(targetVmid);
        vmhostService.addVmHostTask(vmhost.getId(), task.getId());
        UnifiedLogger.log(UnifiedLogger.LogType.TASK_MIGRATE_VM,
                "迁移任务入队, taskId={}, hostId={}, sourceNodeId={}, targetNodeId={}, batchId={}",
                task.getId(), hostId, vmhost.getNodeid(), targetNode.getId(), batchId);
        return VmMigrationBatchDto.Item.accepted(hostId, task.getId());
    }

    private Set<Integer> reservedTargetVmids(Integer targetNodeId) {
        Set<Integer> reserved = new LinkedHashSet<>();
        QueryWrapper<Task> wrapper = new QueryWrapper<>();
        wrapper.eq("type", TaskType.MIGRATE_VM);
        wrapper.in("status", 0, 1, 3);
        List<Task> tasks = taskService.list(wrapper);
        if (tasks == null) {
            return reserved;
        }
        for (Task task : tasks) {
            Map<Object, Object> params = safeParams(task);
            if (!"CANCELLED".equals(stringValue(params.get("stage")))
                    && Objects.equals(targetNodeId, toInteger(params.get("targetNodeId")))) {
                Integer vmid = toInteger(params.get("targetVmid"));
                if (vmid != null && vmid > 0) {
                    reserved.add(vmid);
                }
            }
        }
        return reserved;
    }

    private String checkMigratable(Vmhost vmhost, Master targetNode, List<String> runningStages) {
        if (Objects.equals(vmhost.getNodeid(), targetNode.getId())) {
            return "目标宿主机与源宿主机相同";
        }
        if (targetNode.getStatus() == null || targetNode.getStatus() != 0) {
            return "目标宿主机状态异常";
        }
        if (vmhost.getIfnat() != null && vmhost.getIfnat() == 1
                && (targetNode.getNaton() == null || targetNode.getNaton() != 1)) {
            return "NAT机器迁移要求目标宿主机已开启NAT";
        }
        Integer stage = vmhost.getStatus();
        if (stage != null && (stage == 12 || stage == 13 || stage == 14)) {
            return "虚拟机正在执行其他任务";
        }
        // 同一批里重复提交同一台机器直接拒绝，避免排队后互相抢资源
        if (runningStages.contains(String.valueOf(vmhost.getId()))) {
            return "该虚拟机已在本批次中";
        }
        QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("type", TaskType.MIGRATE_VM);
        queryWrapper.eq("hostid", vmhost.getId());
        queryWrapper.in("status", 0, 1);
        queryWrapper.last("limit 1");
        if (taskService.getOne(queryWrapper) != null) {
            return "该虚拟机已有迁移任务在执行或排队";
        }
        runningStages.add(String.valueOf(vmhost.getId()));
        return null;
    }

    private List<Integer> resolveHostIds(VmMigrationParams params) {
        LinkedHashSet<Integer> hostIds = new LinkedHashSet<>();
        if (params.getHostIds() != null) {
            for (Integer hostId : params.getHostIds()) {
                if (hostId != null) {
                    hostIds.add(hostId);
                }
            }
        }
        if (hostIds.isEmpty() && params.getHostId() != null) {
            hostIds.add(params.getHostId());
        }
        return new ArrayList<>(hostIds);
    }

    public String resolveTargetStorage(String targetStorage, Master targetNode) {
        String normalized = normalizeStorageText(targetStorage);
        if (normalized != null && !"auto".equalsIgnoreCase(normalized)) {
            return normalized;
        }
        String nodeStorage = targetNode == null ? null : normalizeStorageText(targetNode.getAutoStorage());
        if (nodeStorage != null && !"auto".equalsIgnoreCase(nodeStorage)) {
            return nodeStorage;
        }
        return DEFAULT_CREATE_VM_STORAGE;
    }

    private String normalizeStorageText(String storage) {
        if (storage == null) {
            return null;
        }
        String result = storage.trim();
        return result.isEmpty() ? null : result;
    }

    @Override
    public List<VmMigrationTaskView> listTasks(Integer taskId, Integer hostId, String batchId, Integer page, Integer size) {
        QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("type", TaskType.MIGRATE_VM);
        if (taskId != null) {
            queryWrapper.eq("id", taskId);
        }
        if (hostId != null) {
            queryWrapper.eq("hostid", hostId);
        }
        if (StringUtils.isNotBlank(batchId)) {
            queryWrapper.like("params", batchId.trim());
        }
        queryWrapper.orderByDesc("id");
        int current = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        Page<Task> taskPage = taskService.getTaskList(current, pageSize, queryWrapper);
        List<VmMigrationTaskView> views = new ArrayList<>();
        if (taskPage == null || taskPage.getRecords() == null) {
            return views;
        }
        for (Task task : taskPage.getRecords()) {
            views.add(toView(task));
        }
        return views;
    }

    @Override
    public VmMigrationTaskView getTaskView(Integer taskId) {
        Task task = taskId == null ? null : taskService.getById(taskId);
        if (task == null || !TaskType.MIGRATE_VM.equals(task.getType())) {
            return null;
        }
        return toView(task);
    }

    private VmMigrationTaskView toView(Task task) {
        Map<Object, Object> params = safeParams(task);
        VmMigrationTaskView view = new VmMigrationTaskView();
        view.setTaskId(task.getId());
        view.setHostId(task.getHostid());
        view.setBatchId(stringValue(params.get("batchId")));
        view.setSourceNodeId(toInteger(params.get("sourceNodeId")));
        view.setTargetNodeId(toInteger(params.get("targetNodeId")));
        view.setSourceVmid(toInteger(params.get("sourceVmid")));
        view.setTargetVmid(toInteger(params.get("targetVmid")));
        view.setTargetStorage(stringValue(params.get("targetStorage")));
        view.setStatus(task.getStatus());
        String stage = resolveStage(task, params);
        view.setStage(stage);
        view.setStageText(STAGE_TEXT.getOrDefault(stage, stage));
        view.setProgress(toInteger(params.get("progress")));
        view.setRetryCount(toInteger(params.get("retryCount")));
        view.setLastFailedStep(stringValue(params.get("lastFailedStep")));
        view.setError(task.getError());
        view.setResumable(isResumable(task, stage));
        view.setCreateDate(task.getCreateDate());
        view.setUpdateTime(longValue(params.get("updateTime")));
        view.setQueuePosition(resolveQueuePosition(task));
        view.setLogs(taskLogLines(task, params));
        view.setParams(TaskPasswordValues.sanitizeForResponse(params));
        Vmhost vmhost = task.getHostid() == null ? null : vmhostService.getById(task.getHostid());
        if (vmhost != null) {
            view.setHostName(hostName(vmhost));
        }
        Master targetNode = view.getTargetNodeId() == null ? null : masterService.getById(view.getTargetNodeId());
        if (targetNode != null) {
            view.setTargetNodeName(targetNode.getNodeName());
        }
        return view;
    }

    @Override
    public VmMigrationLogView getTaskLog(Integer taskId, String offset) {
        Task task = taskId == null ? null : taskService.getById(taskId);
        VmMigrationLogView view = new VmMigrationLogView();
        view.setTaskId(taskId);
        if (task == null || !TaskType.MIGRATE_VM.equals(task.getType())) {
            return view;
        }
        Map<Object, Object> params = safeParams(task);
        String stage = resolveStage(task, params);
        view.setStage(stage);
        view.setStageText(STAGE_TEXT.getOrDefault(stage, stage));
        view.setProgress(toInteger(params.get("progress")));
        view.setStatus(statusText(task.getStatus()));

        // 主控日志按绝对行序号定位；源/目标被控日志各自保留 byte offset。
        LogCursor cursor = LogCursor.parse(offset, taskId);
        List<String> mainLines = taskLogLines(task, params);
        Long storedBase = longValue(params.get("logLineBase"));
        long mainBase = storedBase == null ? 0 : Math.max(0, storedBase);
        long mainEnd = mainBase + mainLines.size();
        long mainOffset = cursor.mainOffset < 0 ? mainBase : Math.min(mainEnd, Math.max(mainBase, cursor.mainOffset));
        List<String> lines = new ArrayList<>();
        lines.addAll(mainLines.subList((int) (mainOffset - mainBase), mainLines.size()));
        cursor.mainOffset = mainEnd;
        fetchAgentLogs(task, params, cursor, lines);
        view.setNextOffset(cursor.encode(taskId));
        view.setLogs(lines);
        return view;
    }

    /**
     * 被控端日志按阶段落在源节点或目标节点，两侧都取一次再按阶段拼接
     */
    private void fetchAgentLogs(Task task, Map<Object, Object> params, LogCursor cursor, List<String> lines) {
        Integer sourceNodeId = toInteger(params.get("sourceNodeId"));
        Integer targetNodeId = toInteger(params.get("targetNodeId"));
        String taskId = String.valueOf(task.getId());
        if (sourceNodeId != null) {
            cursor.sourceOffset = fetchAgentLog(sourceNodeId, taskId, cursor.sourceOffset, lines);
        }
        if (targetNodeId != null && !Objects.equals(targetNodeId, sourceNodeId)) {
            cursor.targetOffset = fetchAgentLog(targetNodeId, taskId, cursor.targetOffset, lines);
        }
    }

    private long fetchAgentLog(Integer nodeId, String taskId, long offset, List<String> lines) {
        Master node = masterService.getById(nodeId);
        if (node == null) {
            return offset;
        }
        try {
            JSONObject result = requestAgentLog(node, taskId, offset);
            JSONObject data = result == null ? null : result.getJSONObject("data");
            if (data == null || !Integer.valueOf(200).equals(result.getInteger("code"))) {
                return offset;
            }
            Long nextOffset = data.getLong("next_offset");
            if (nextOffset == null || nextOffset < 0) {
                return offset;
            }
            String content = data.getString("content");
            if (StringUtils.isNotBlank(content)) {
                for (String line : content.split("\\r?\\n")) {
                    if (StringUtils.isNotBlank(line)) {
                        lines.add("[" + node.getNodeName() + "] " + line.trim());
                    }
                }
            }
            return nextOffset;
        } catch (Exception e) {
            UnifiedLogger.warn(UnifiedLogger.LogType.TASK_MIGRATE_VM,
                    "拉取被控端迁移日志失败, taskId={}, nodeId={}, error={}", taskId, nodeId, e.getMessage());
        }
        return offset;
    }

    protected JSONObject requestAgentLog(Master node, String taskId, long offset) {
        return ClientApiUtil.getMigrationLog(node.getHost(), configService.getToken(), node.getControllerPort(), taskId, offset);
    }

    private static class LogCursor {
        private long mainOffset = -1;
        private long sourceOffset = -1;
        private long targetOffset = -1;

        private static LogCursor parse(String value, Integer taskId) {
            LogCursor cursor = new LogCursor();
            if (StringUtils.isBlank(value)) {
                return cursor;
            }
            String[] parts = value.split(":", -1);
            if (parts.length == 5 && "v1".equals(parts[0]) && String.valueOf(taskId).equals(parts[1])) {
                try {
                    long main = Long.parseLong(parts[2]);
                    long source = Long.parseLong(parts[3]);
                    long target = Long.parseLong(parts[4]);
                    if (main >= 0 && source >= -1 && target >= -1) {
                        cursor.mainOffset = main;
                        cursor.sourceOffset = source;
                        cursor.targetOffset = target;
                    }
                } catch (NumberFormatException ignored) {
                    // 旧数值游标或无效游标不能换算为 byte offset，安全地重新取尾部。
                }
            }
            return cursor;
        }

        private String encode(Integer taskId) {
            return "v1:" + taskId + ":" + mainOffset + ":" + sourceOffset + ":" + targetOffset;
        }
    }

    @Override
    public Task resume(Integer taskId) {
        Task task = taskId == null ? null : taskService.getById(taskId);
        if (task == null || !TaskType.MIGRATE_VM.equals(task.getType())) {
            throw new IllegalStateException("迁移任务不存在");
        }
        if (!Integer.valueOf(3).equals(task.getStatus())) {
            throw new IllegalStateException("只有失败的迁移任务可以从断点继续");
        }
        Map<Object, Object> params = safeParams(task);
        String failedStage = stringValue(params.get("lastFailedStep"));
        if (failedStage == null || !RESUMABLE_STAGES.contains(failedStage)) {
            throw new IllegalStateException("当前失败步骤不支持续跑，请重新发起迁移");
        }
        QueryWrapper<Task> runningWrapper = new QueryWrapper<>();
        runningWrapper.eq("type", TaskType.MIGRATE_VM);
        runningWrapper.eq("hostid", task.getHostid());
        runningWrapper.eq("status", 1);
        runningWrapper.ne("id", task.getId());
        runningWrapper.last("limit 1");
        if (taskService.getOne(runningWrapper) != null) {
            throw new IllegalStateException("该虚拟机还有迁移任务在执行");
        }
        // 未提交的目标网络会在失败时回滚，启动失败必须先重建，不能直接沿用源端记录启动。
        String resumeStage = "START_TARGET_VM".equals(failedStage) && !Boolean.TRUE.equals(params.get("targetCommitted"))
                ? "NETWORK_REBIND" : failedStage;
        params.put("resumeFromStage", resumeStage);
        params.put("stage", "PENDING");
        params.put("progress", 0);
        params.put("updateTime", System.currentTimeMillis());
        appendTaskLog(task, params, "从 " + STAGE_TEXT.getOrDefault(resumeStage, resumeStage) + " 步骤继续");
        task.setParams(params);
        task.setStatus(0);
        task.setError(null);
        taskService.updateTask(task);
        UnifiedLogger.log(UnifiedLogger.LogType.TASK_MIGRATE_VM,
                "迁移任务从断点继续, taskId={}, resumeFromStage={}", task.getId(), resumeStage);
        return task;
    }

    @Override
    public boolean cancel(Integer taskId) {
        Task task = taskId == null ? null : taskService.getById(taskId);
        if (task == null || !TaskType.MIGRATE_VM.equals(task.getType())) {
            return false;
        }
        if (!Integer.valueOf(0).equals(task.getStatus())) {
            return false;
        }
        Map<Object, Object> params = safeParams(task);
        params.put("stage", "CANCELLED");
        task.setParams(params);
        task.setStatus(3);
        task.setError("用户取消了排队中的迁移任务");
        taskService.updateTask(task);
        return true;
    }

    @Override
    public boolean hasRunningTask() {
        QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("type", TaskType.MIGRATE_VM);
        queryWrapper.eq("status", 1);
        queryWrapper.last("limit 1");
        return taskService.getOne(queryWrapper) != null;
    }

    public boolean isResumable(Task task, String stage) {
        return Integer.valueOf(3).equals(task.getStatus()) && stage != null && RESUMABLE_STAGES.contains(stage);
    }

    private String resolveStage(Task task, Map<Object, Object> params) {
        String stage = stringValue(params.get("stage"));
        if (stage == null) {
            stage = "PENDING";
        }
        if ("FAILED".equals(stage)) {
            String failedStep = stringValue(params.get("lastFailedStep"));
            return failedStep == null ? stage : failedStep;
        }
        if ("SUCCESS".equals(stage)) {
            return "DONE";
        }
        return stage;
    }

    private String statusText(Integer status) {
        if (status == null) {
            return "UNKNOWN";
        }
        switch (status) {
            case 0:
                return "QUEUED";
            case 1:
                return "RUNNING";
            case 2:
                return "SUCCESS";
            case 3:
                return "FAILED";
            default:
                return "UNKNOWN";
        }
    }

    private Integer resolveQueuePosition(Task task) {
        if (!Integer.valueOf(0).equals(task.getStatus())) {
            return null;
        }
        QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("type", TaskType.MIGRATE_VM);
        queryWrapper.eq("status", 0);
        queryWrapper.le("id", task.getId());
        long count = taskService.count(queryWrapper);
        return (int) count;
    }

    @SuppressWarnings("unchecked")
    private List<String> taskLogLines(Task task, Map<Object, Object> params) {
        Object value = params.get("logLines");
        if (value instanceof List) {
            return new ArrayList<>((List<String>) value);
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public void appendTaskLog(Task task, Map<Object, Object> params, String message) {
        if (StringUtils.isBlank(message)) {
            return;
        }
        Object value = params.get("logLines");
        List<String> lines = value instanceof List ? new ArrayList<>((List<String>) value) : new ArrayList<>();
        lines.add(message);
        // 只保留最近的记录，避免 params 无限膨胀
        Long storedBase = longValue(params.get("logLineBase"));
        long base = storedBase == null ? 0 : Math.max(0, storedBase);
        while (lines.size() > 500) {
            lines.remove(0);
            base++;
        }
        params.put("logLines", lines);
        params.put("logLineBase", base);
    }

    private Map<Object, Object> safeParams(Task task) {
        Map<Object, Object> params = task.getParams();
        return params == null ? new HashMap<>() : params;
    }

    private String hostName(Vmhost vmhost) {
        return StringUtils.defaultIfBlank(vmhost.getHostname(), "vm-" + vmhost.getVmid());
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String result = String.valueOf(value).trim();
        return result.isEmpty() ? null : result;
    }
}
