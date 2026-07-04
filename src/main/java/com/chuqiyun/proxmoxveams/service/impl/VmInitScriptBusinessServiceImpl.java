package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dao.VmhostDao;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.VmInitScript;
import com.chuqiyun.proxmoxveams.entity.VmInitScriptRecord;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.TaskService;
import com.chuqiyun.proxmoxveams.service.VmInitScriptBusinessService;
import com.chuqiyun.proxmoxveams.service.VmInitScriptRecordService;
import com.chuqiyun.proxmoxveams.service.VmInitScriptService;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.chuqiyun.proxmoxveams.constant.TaskType.RUN_QEMU_INIT_SCRIPT;

/**
 * @Author: 鏄熺
 * @Description: 虚拟机初始化脚本业务服务
 * @DateTime: 2026/7/3 20:47
 */
@Service("vmInitScriptBusinessService")
public class VmInitScriptBusinessServiceImpl implements VmInitScriptBusinessService {
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_SKIPPED = "skipped";
    private static final String DEFAULT_SCRIPT_TYPE = "shell";
    private static final String SCRIPT_TYPE_AUTO = "auto";
    private static final String SCRIPT_TYPE_SHELL = "shell";
    private static final String SCRIPT_TYPE_BASH = "bash";
    private static final String SCRIPT_TYPE_POWERSHELL = "powershell";
    private static final String SCRIPT_TYPE_CMD = "cmd";
    private static final String TARGET_OS_AUTO = "auto";
    private static final String TARGET_OS_LINUX = "linux";
    private static final String TARGET_OS_WINDOWS = "windows";
    private static final String DEFAULT_RUN_MODE = "qemu_agent";
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;
    private static final long AGENT_WAIT_TIMEOUT_MS = 300_000L;
    private static final long POLL_INTERVAL_MS = 2000L;
    private static final int LOG_MAX_LENGTH = 16000;

    @Resource
    private VmInitScriptService vmInitScriptService;
    @Resource
    private VmInitScriptRecordService vmInitScriptRecordService;
    @Resource
    private VmhostDao vmhostDao;
    @Resource
    private MasterService masterService;
    @Resource
    private TaskService taskService;

    @Override
    public UnifiedResultDto<Object> addScript(VmInitScript script) {
        String error = normalizeAndValidateScript(script, false);
        if (error != null) {
            return invalidParam(error);
        }
        long now = System.currentTimeMillis();
        script.setStatus(script.getStatus() == null ? 1 : script.getStatus());
        script.setCreateTime(now);
        script.setUpdateTime(now);
        return vmInitScriptService.save(script)
                ? new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, script)
                : new UnifiedResultDto<>(UnifiedResultCode.ERROR_UNKNOWN, null, "初始化脚本保存失败");
    }

    @Override
    public UnifiedResultDto<Object> updateScript(VmInitScript script) {
        if (script == null || script.getId() == null) {
            return invalidParam("scriptId不能为空");
        }
        VmInitScript old = vmInitScriptService.getById(script.getId());
        if (old == null || Objects.equals(old.getStatus(), 0)) {
            return invalidParam("初始化脚本不存在或已删除: scriptId=" + script.getId());
        }
        mergeScript(old, script);
        String error = normalizeAndValidateScript(old, true);
        if (error != null) {
            return invalidParam(error);
        }
        old.setUpdateTime(System.currentTimeMillis());
        return vmInitScriptService.updateById(old)
                ? new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, old)
                : new UnifiedResultDto<>(UnifiedResultCode.ERROR_UNKNOWN, null, "初始化脚本更新失败");
    }

    @Override
    public UnifiedResultDto<Object> deleteScript(Integer scriptId) {
        if (scriptId == null) {
            return invalidParam("scriptId不能为空");
        }
        VmInitScript script = vmInitScriptService.getById(scriptId);
        if (script == null || Objects.equals(script.getStatus(), 0)) {
            return invalidParam("初始化脚本不存在或已删除: scriptId=" + scriptId);
        }
        script.setStatus(0);
        script.setUpdateTime(System.currentTimeMillis());
        return vmInitScriptService.updateById(script)
                ? new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, null)
                : new UnifiedResultDto<>(UnifiedResultCode.ERROR_UNKNOWN, null, "初始化脚本删除失败");
    }

    @Override
    public UnifiedResultDto<Object> validateScripts(List<Integer> scriptIds) {
        if (scriptIds == null || scriptIds.isEmpty()) {
            return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, null);
        }
        for (Integer scriptId : scriptIds) {
            if (scriptId == null) {
                return invalidParam("initScriptIds中存在空脚本ID");
            }
            VmInitScript script = vmInitScriptService.getById(scriptId);
            if (script == null || !Objects.equals(script.getStatus(), 1)) {
                return invalidParam("初始化脚本不存在或未启用: scriptId=" + scriptId);
            }
            if (!DEFAULT_RUN_MODE.equalsIgnoreCase(defaultString(script.getRunMode(), DEFAULT_RUN_MODE))) {
                return invalidParam("当前仅支持qemu_agent初始化脚本: scriptId=" + scriptId);
            }
        }
        return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, null);
    }

    @Override
    public void createRunTasks(Integer hostId, Integer vmid, Integer nodeId, List<Integer> scriptIds, String triggerType) {
        if (hostId == null || vmid == null || nodeId == null || scriptIds == null || scriptIds.isEmpty()) {
            return;
        }
        for (Integer scriptId : scriptIds) {
            VmInitScript script = vmInitScriptService.getById(scriptId);
            if (script == null || !Objects.equals(script.getStatus(), 1)) {
                continue;
            }
            VmInitScriptRecord record = new VmInitScriptRecord();
            long now = System.currentTimeMillis();
            record.setScriptId(scriptId);
            record.setHostId(hostId);
            record.setVmid(vmid);
            record.setNodeId(nodeId);
            record.setTriggerType(defaultString(triggerType, "manual"));
            record.setStatus(STATUS_PENDING);
            record.setRunCount(0);
            record.setCreateTime(now);
            record.setUpdateTime(now);
            if (!vmInitScriptRecordService.save(record)) {
                continue;
            }
            Task task = new Task();
            task.setNodeid(nodeId);
            task.setVmid(vmid);
            task.setHostid(hostId);
            task.setType(RUN_QEMU_INIT_SCRIPT);
            task.setStatus(0);
            task.setCreateDate(now);
            HashMap<Object, Object> params = new HashMap<>();
            params.put("recordId", record.getId());
            params.put("scriptId", scriptId);
            params.put("triggerType", record.getTriggerType());
            task.setParams(params);
            taskService.save(task);
            addVmHostTask(hostId, task.getId());
        }
    }

    @Override
    public UnifiedResultDto<Object> createManualRunTasks(Integer hostId, List<Integer> scriptIds) {
        if (hostId == null) {
            return invalidParam("hostId不能为空");
        }
        Vmhost vmhost = vmhostDao.selectById(hostId);
        if (vmhost == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_VM_NOT_EXIST, null);
        }
        UnifiedResultDto<Object> validateResult = validateScripts(scriptIds);
        if (validateResult.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return validateResult;
        }
        createRunTasks(vmhost.getId(), vmhost.getVmid(), vmhost.getNodeid(), scriptIds, "manual");
        return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, null);
    }

    @Override
    public boolean executeTask(Task task) {
        if (task == null || task.getParams() == null || task.getParams().get("recordId") == null) {
            return false;
        }
        Integer recordId = toInteger(task.getParams().get("recordId"));
        VmInitScriptRecord record = vmInitScriptRecordService.getById(recordId);
        if (record == null) {
            return false;
        }
        VmInitScript script = vmInitScriptService.getById(record.getScriptId());
        if (script == null || !Objects.equals(script.getStatus(), 1)) {
            failRecord(record, "初始化脚本不存在或未启用: scriptId=" + record.getScriptId(), null, null, null);
            return false;
        }
        Vmhost vmhost = vmhostDao.selectById(record.getHostId());
        Master node = masterService.getById(record.getNodeId());
        if (vmhost == null || node == null) {
            failRecord(record, "虚拟机或节点不存在", null, null, null);
            return false;
        }
        return executeScript(record, script, vmhost, node);
    }

    @Override
    public List<Integer> normalizeScriptIds(Integer scriptId, List<Integer> scriptIds) {
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        if (scriptIds != null) {
            for (Integer id : scriptIds) {
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        if (scriptId != null) {
            ids.add(scriptId);
        }
        return new ArrayList<>(ids);
    }

    @Override
    public List<VmInitScriptRecord> getRecords(Integer hostId, Integer scriptId) {
        QueryWrapper<VmInitScriptRecord> queryWrapper = new QueryWrapper<>();
        if (hostId != null) {
            queryWrapper.eq("host_id", hostId);
        }
        if (scriptId != null) {
            queryWrapper.eq("script_id", scriptId);
        }
        queryWrapper.orderByDesc("id");
        Page<VmInitScriptRecord> page = vmInitScriptRecordService.page(new Page<>(1, 100), queryWrapper);
        return page == null ? Collections.emptyList() : page.getRecords();
    }

    private boolean executeScript(VmInitScriptRecord record, VmInitScript script, Vmhost vmhost, Master node) {
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        HashMap<String, String> cookieMap = masterService.getMasterCookieMap(node.getId());
        Integer timeoutSeconds = script.getTimeoutSeconds() == null || script.getTimeoutSeconds() <= 0
                ? DEFAULT_TIMEOUT_SECONDS : script.getTimeoutSeconds();
        startRecord(record);
        try {
            ScriptExecutionPlan plan = buildExecutionPlan(script, vmhost);
            if (plan.skipped) {
                skipRecord(record, plan.skipReason);
                return true;
            }
            waitVmRunningAndAgentReady(proxmoxApiUtil, node, cookieMap, vmhost);
            JSONObject execResult = runGuestScript(proxmoxApiUtil, node, cookieMap, vmhost.getVmid(), plan);
            Integer pid = extractPid(execResult);
            record.setPid(pid);
            record.setUpdateTime(System.currentTimeMillis());
            vmInitScriptRecordService.updateById(record);
            JSONObject status = waitExecStatus(proxmoxApiUtil, node, cookieMap, vmhost, pid, timeoutSeconds);
            Integer exitCode = extractExitCode(status);
            String stdout = extractOutput(status, "out-data", "out_data", "stdout");
            String stderr = extractOutput(status, "err-data", "err_data", "stderr");
            if (exitCode != null && exitCode == 0) {
                successRecord(record, exitCode, stdout, stderr);
                return true;
            }
            failRecord(record, "脚本执行失败，exitCode=" + exitCode, exitCode, stdout, stderr);
            return false;
        } catch (Exception e) {
            failRecord(record, buildExecuteError(e, script, vmhost), null, null, null);
            return false;
        }
    }

    private void waitVmRunningAndAgentReady(ProxmoxApiUtil proxmoxApiUtil, Master node,
                                            HashMap<String, String> cookieMap, Vmhost vmhost) {
        long endTime = System.currentTimeMillis() + AGENT_WAIT_TIMEOUT_MS;
        Exception lastException = null;
        while (System.currentTimeMillis() <= endTime) {
            try {
                if (isVmRunning(proxmoxApiUtil, node, cookieMap, vmhost.getVmid())) {
                    proxmoxApiUtil.guestPing(node, cookieMap, vmhost.getVmid());
                    return;
                }
            } catch (Exception e) {
                lastException = e;
            }
            sleep(POLL_INTERVAL_MS);
        }
        String message = "等待虚拟机运行或QEMU Guest Agent就绪超时";
        if (lastException != null && StringUtils.isNotBlank(lastException.getMessage())) {
            message += ": " + lastException.getMessage();
        }
        throw new IllegalStateException(message);
    }

    private boolean isVmRunning(ProxmoxApiUtil proxmoxApiUtil, Master node, HashMap<String, String> cookieMap, Integer vmid) {
        JSONObject status = proxmoxApiUtil.getVmStatus(node, cookieMap, vmid);
        JSONObject data = status == null ? null : status.getJSONObject("data");
        return data != null && "running".equalsIgnoreCase(data.getString("status"));
    }

    private JSONObject waitExecStatus(ProxmoxApiUtil proxmoxApiUtil, Master node, HashMap<String, String> cookieMap,
                                      Vmhost vmhost, Integer pid, Integer timeoutSeconds) {
        if (pid == null) {
            throw new IllegalStateException("QEMU Guest Agent未返回脚本PID");
        }
        long endTime = System.currentTimeMillis() + timeoutSeconds * 1000L;
        JSONObject lastStatus = null;
        while (System.currentTimeMillis() <= endTime) {
            lastStatus = proxmoxApiUtil.guestExecStatus(node, cookieMap, vmhost.getVmid(), pid);
            JSONObject data = lastStatus == null ? null : lastStatus.getJSONObject("data");
            if (data != null && data.getBooleanValue("exited")) {
                return data;
            }
            sleep(POLL_INTERVAL_MS);
        }
        throw new IllegalStateException("脚本执行超时: timeoutSeconds=" + timeoutSeconds + ", pid=" + pid
                + ", lastStatus=" + (lastStatus == null ? "" : lastStatus.toJSONString()));
    }

    private void startRecord(VmInitScriptRecord record) {
        long now = System.currentTimeMillis();
        record.setStatus(STATUS_RUNNING);
        record.setStartTime(now);
        record.setUpdateTime(now);
        record.setRunCount(record.getRunCount() == null ? 1 : record.getRunCount() + 1);
        vmInitScriptRecordService.updateById(record);
    }

    private void successRecord(VmInitScriptRecord record, Integer exitCode, String stdout, String stderr) {
        finishRecord(record, STATUS_SUCCESS, null, exitCode, stdout, stderr);
    }

    private void skipRecord(VmInitScriptRecord record, String reason) {
        finishRecord(record, STATUS_SKIPPED, reason, 0, null, null);
    }

    private void failRecord(VmInitScriptRecord record, String error, Integer exitCode, String stdout, String stderr) {
        finishRecord(record, STATUS_FAILED, error, exitCode, stdout, stderr);
    }

    private void finishRecord(VmInitScriptRecord record, String status, String error, Integer exitCode, String stdout, String stderr) {
        long now = System.currentTimeMillis();
        record.setStatus(status);
        record.setError(truncate(error));
        record.setExitCode(exitCode);
        record.setStdout(truncate(stdout));
        record.setStderr(truncate(stderr));
        record.setFinishTime(now);
        record.setUpdateTime(now);
        vmInitScriptRecordService.updateById(record);
    }

    private String normalizeAndValidateScript(VmInitScript script, boolean update) {
        if (script == null) {
            return "初始化脚本不能为空";
        }
        if (StringUtils.isBlank(script.getName())) {
            return "脚本名称不能为空";
        }
        if (StringUtils.isBlank(script.getContent()) && StringUtils.isBlank(script.getLinuxContent())
                && StringUtils.isBlank(script.getWindowsContent())) {
            return "脚本内容不能为空";
        }
        script.setName(script.getName().trim());
        script.setScriptType(defaultString(script.getScriptType(), DEFAULT_SCRIPT_TYPE).trim().toLowerCase());
        script.setRunMode(defaultString(script.getRunMode(), DEFAULT_RUN_MODE).trim().toLowerCase());
        script.setTargetOs(defaultString(script.getTargetOs(), TARGET_OS_AUTO).trim().toLowerCase());
        if (!isSupportedScriptType(script.getScriptType())) {
            return "脚本类型仅支持auto/shell/bash/powershell/cmd";
        }
        if (!TARGET_OS_AUTO.equals(script.getTargetOs()) && !TARGET_OS_LINUX.equals(script.getTargetOs())
                && !TARGET_OS_WINDOWS.equals(script.getTargetOs())) {
            return "目标系统仅支持auto/linux/windows";
        }
        if (!DEFAULT_RUN_MODE.equals(script.getRunMode())) {
            return "当前仅支持qemu_agent运行模式";
        }
        fillLegacyContent(script);
        if (script.getTimeoutSeconds() == null || script.getTimeoutSeconds() <= 0) {
            script.setTimeoutSeconds(DEFAULT_TIMEOUT_SECONDS);
        }
        if (script.getTimeoutSeconds() > 3600) {
            return "脚本超时时间不能超过3600秒";
        }
        if (!update && script.getStatus() == null) {
            script.setStatus(1);
        }
        return null;
    }

    private void mergeScript(VmInitScript old, VmInitScript script) {
        if (script.getName() != null) {
            old.setName(script.getName());
        }
        if (script.getScriptType() != null) {
            old.setScriptType(script.getScriptType());
        }
        if (script.getRunMode() != null) {
            old.setRunMode(script.getRunMode());
        }
        if (script.getTargetOs() != null) {
            old.setTargetOs(script.getTargetOs());
        }
        if (script.getContent() != null) {
            old.setContent(script.getContent());
        }
        if (script.getLinuxContent() != null) {
            old.setLinuxContent(script.getLinuxContent());
        }
        if (script.getWindowsContent() != null) {
            old.setWindowsContent(script.getWindowsContent());
        }
        if (script.getTimeoutSeconds() != null) {
            old.setTimeoutSeconds(script.getTimeoutSeconds());
        }
        if (script.getStatus() != null) {
            old.setStatus(script.getStatus());
        }
        if (script.getRemark() != null) {
            old.setRemark(script.getRemark());
        }
    }

    private Integer extractPid(JSONObject execResult) {
        JSONObject data = execResult == null ? null : execResult.getJSONObject("data");
        return data == null ? null : data.getInteger("pid");
    }

    private Integer extractExitCode(JSONObject status) {
        if (status == null) {
            return null;
        }
        Integer exitCode = status.getInteger("exitcode");
        return exitCode == null ? status.getInteger("exit-code") : exitCode;
    }

    private String extractOutput(JSONObject status, String... keys) {
        if (status == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            String value = status.getString(key);
            if (value != null) {
                return decodeOutput(value);
            }
        }
        return null;
    }

    private String decodeOutput(String value) {
        if (value == null) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            String text = new String(decoded, StandardCharsets.UTF_8);
            if (StringUtils.isNotBlank(text)) {
                return text;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return value;
    }

    private Integer toInteger(Object value) {
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? null : Integer.parseInt(value.toString());
    }

    private String truncate(String value) {
        if (value == null || value.length() <= LOG_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, LOG_MAX_LENGTH);
    }

    private String defaultString(String value, String defaultValue) {
        return StringUtils.isBlank(value) ? defaultValue : value;
    }

    private void fillLegacyContent(VmInitScript script) {
        if (StringUtils.isNotBlank(script.getContent())) {
            return;
        }
        if (StringUtils.isNotBlank(script.getLinuxContent())) {
            script.setContent(script.getLinuxContent());
        } else if (StringUtils.isNotBlank(script.getWindowsContent())) {
            script.setContent(script.getWindowsContent());
        }
    }

    private boolean isSupportedScriptType(String scriptType) {
        return SCRIPT_TYPE_AUTO.equals(scriptType) || SCRIPT_TYPE_SHELL.equals(scriptType)
                || SCRIPT_TYPE_BASH.equals(scriptType) || SCRIPT_TYPE_POWERSHELL.equals(scriptType)
                || SCRIPT_TYPE_CMD.equals(scriptType);
    }

    private ScriptExecutionPlan buildExecutionPlan(VmInitScript script, Vmhost vmhost) {
        String vmOs = detectVmOs(vmhost);
        String targetOs = defaultString(script.getTargetOs(), TARGET_OS_AUTO).trim().toLowerCase();
        String scriptType = defaultString(script.getScriptType(), DEFAULT_SCRIPT_TYPE).trim().toLowerCase();
        if (!TARGET_OS_AUTO.equals(targetOs) && !targetOs.equals(vmOs)) {
            return ScriptExecutionPlan.skip(vmOs, scriptType, "脚本目标系统为" + targetOs + "，当前虚拟机为" + vmOs + "，已跳过");
        }

        if (TARGET_OS_WINDOWS.equals(vmOs)) {
            String content = firstText(script.getWindowsContent(), isWindowsScriptType(scriptType) ? script.getContent() : null);
            if (StringUtils.isBlank(content)) {
                return ScriptExecutionPlan.skip(vmOs, scriptType, "当前虚拟机为windows，但脚本未配置Windows命令，已跳过");
            }
            String commandType = SCRIPT_TYPE_CMD.equals(scriptType) ? SCRIPT_TYPE_CMD : SCRIPT_TYPE_POWERSHELL;
            return ScriptExecutionPlan.run(vmOs, commandType, content);
        }

        String content = firstText(script.getLinuxContent(), isLinuxScriptType(scriptType) ? script.getContent() : null);
        if (StringUtils.isBlank(content)) {
            return ScriptExecutionPlan.skip(vmOs, scriptType, "当前虚拟机为linux，但脚本未配置Linux命令，已跳过");
        }
        String commandType = SCRIPT_TYPE_BASH.equals(scriptType) ? SCRIPT_TYPE_BASH : SCRIPT_TYPE_SHELL;
        return ScriptExecutionPlan.run(vmOs, commandType, content);
    }

    private JSONObject runGuestScript(ProxmoxApiUtil proxmoxApiUtil, Master node, HashMap<String, String> cookieMap,
                                      Integer vmid, ScriptExecutionPlan plan) {
        if (SCRIPT_TYPE_BASH.equals(plan.commandType)) {
            return proxmoxApiUtil.guestExecBash(node, cookieMap, vmid, plan.content);
        }
        if (SCRIPT_TYPE_POWERSHELL.equals(plan.commandType)) {
            return proxmoxApiUtil.guestExecPowerShell(node, cookieMap, vmid, plan.content);
        }
        if (SCRIPT_TYPE_CMD.equals(plan.commandType)) {
            return proxmoxApiUtil.guestExecCmd(node, cookieMap, vmid, plan.content);
        }
        return proxmoxApiUtil.guestExecShell(node, cookieMap, vmid, plan.content);
    }

    private String detectVmOs(Vmhost vmhost) {
        String value = (defaultString(vmhost.getOsType(), "") + " "
                + defaultString(vmhost.getOsName(), "") + " "
                + defaultString(vmhost.getOs(), "")).toLowerCase();
        if (value.contains("win")) {
            return TARGET_OS_WINDOWS;
        }
        return TARGET_OS_LINUX;
    }

    private boolean isWindowsScriptType(String scriptType) {
        return SCRIPT_TYPE_AUTO.equals(scriptType) || SCRIPT_TYPE_POWERSHELL.equals(scriptType) || SCRIPT_TYPE_CMD.equals(scriptType);
    }

    private boolean isLinuxScriptType(String scriptType) {
        return SCRIPT_TYPE_AUTO.equals(scriptType) || SCRIPT_TYPE_SHELL.equals(scriptType) || SCRIPT_TYPE_BASH.equals(scriptType);
    }

    private String firstText(String first, String second) {
        return StringUtils.isNotBlank(first) ? first : second;
    }

    private String buildExecuteError(Exception e, VmInitScript script, Vmhost vmhost) {
        String message = e == null ? null : e.getMessage();
        String vmOs = detectVmOs(vmhost);
        String scriptType = script == null ? DEFAULT_SCRIPT_TYPE : defaultString(script.getScriptType(), DEFAULT_SCRIPT_TYPE);
        return "初始化脚本执行失败: vmOs=" + vmOs + ", scriptType=" + scriptType
                + ", error=" + defaultString(message, "未知错误")
                + "。如果错误为596 Broken pipe，通常是QEMU Guest Agent执行命令失败、来宾系统命令不存在、guest-exec被禁用、Agent版本不支持或脚本类型与系统不匹配。";
    }

    private UnifiedResultDto<Object> invalidParam(String message) {
        return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null, message);
    }

    private boolean addVmHostTask(Object hostId, Object taskId) {
        if (hostId == null || taskId == null) {
            return false;
        }
        Vmhost vmhost = vmhostDao.selectById(Long.parseLong(hostId.toString()));
        if (vmhost == null) {
            return false;
        }
        Map<Object, Object> nowTask = vmhost.getTask() == null ? new HashMap<>() : vmhost.getTask();
        nowTask.put(System.currentTimeMillis(), taskId);
        vmhost.setTask(nowTask);
        return vmhostDao.updateById(vmhost) > 0;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("初始化脚本执行被中断", e);
        }
    }

    private static class ScriptExecutionPlan {
        private final boolean skipped;
        private final String vmOs;
        private final String commandType;
        private final String content;
        private final String skipReason;

        private ScriptExecutionPlan(boolean skipped, String vmOs, String commandType, String content, String skipReason) {
            this.skipped = skipped;
            this.vmOs = vmOs;
            this.commandType = commandType;
            this.content = content;
            this.skipReason = skipReason;
        }

        private static ScriptExecutionPlan run(String vmOs, String commandType, String content) {
            return new ScriptExecutionPlan(false, vmOs, commandType, content, null);
        }

        private static ScriptExecutionPlan skip(String vmOs, String commandType, String reason) {
            return new ScriptExecutionPlan(true, vmOs, commandType, null, reason);
        }
    }
}
