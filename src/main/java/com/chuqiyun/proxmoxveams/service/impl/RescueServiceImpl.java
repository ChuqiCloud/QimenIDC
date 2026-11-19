package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chuqiyun.proxmoxveams.dao.RescueVmStateDao;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.RescueVmState;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.RescueService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import com.chuqiyun.proxmoxveams.utils.RescueImageCatalog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** PVE 节点直连镜像站的救援系统工作流。 */
@Slf4j
@Service("rescueService")
public class RescueServiceImpl implements RescueService {
    private static final long TASK_TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private static final long STOP_TIMEOUT_MILLIS = 5 * 60 * 1000L;
    private static final long POLL_INTERVAL_MILLIS = 2000L;
    private static final Map<Integer, Object> LOCKS = new ConcurrentHashMap<>();

    @Resource
    private RescueVmStateDao rescueVmStateDao;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private MasterService masterService;
    @Resource
    @Qualifier("workflowExecutor")
    private TaskExecutor workflowExecutor;

    @Override
    public List<Map<String, Object>> listImages() {
        return RescueImageCatalog.asMaps();
    }

    @Override
    public RescueVmState getState(Integer hostId) {
        if (hostId == null) {
            return null;
        }
        return rescueVmStateDao.selectOne(new QueryWrapper<RescueVmState>().eq("host_id", hostId));
    }

    @Override
    public RescueVmState enable(Integer hostId, String imageId, String storage) {
        RescueImageCatalog.Image image = RescueImageCatalog.get(imageId);
        if (image == null) {
            throw new IllegalArgumentException("不支持的救援镜像");
        }
        Vmhost vmhost = requireVm(hostId);
        Master node = requireNode(vmhost.getNodeid());
        Object lock = LOCKS.computeIfAbsent(hostId, ignored -> new Object());
        RescueVmState state;
        synchronized (lock) {
            state = getState(hostId);
            if (state != null && isBusy(state.getStatus())) {
                throw new IllegalStateException("该虚拟机已有救援任务正在执行: " + state.getStatus());
            }
            boolean created = state == null;
            if (state == null) {
                state = new RescueVmState();
                state.setHostId(hostId);
                state.setCreateTime(System.currentTimeMillis());
            }
            state.setNodeId(node.getId());
            state.setVmid(vmhost.getVmid());
            state.setImageId(image.id());
            state.setImageUrl(image.url());
            state.setFileName(image.fileName());
            state.setStorage(blankToNull(storage));
            state.setOriginalBoot(null);
            state.setOriginalRunning(null);
            state.setIsoVolume(null);
            state.setError(null);
            state.setStatus("ENABLING");
            state.setUpdateTime(System.currentTimeMillis());
            if (created) {
                rescueVmStateDao.insert(state);
            } else {
                rescueVmStateDao.updateById(state);
            }
        }
        final Long stateId = state.getId();
        final Integer nodeId = node.getId();
        try {
            submit(() -> processEnable(stateId, hostId, nodeId));
        } catch (IllegalStateException e) {
            markFailed(stateId, e.getMessage());
            throw e;
        }
        return state;
    }

    @Override
    public RescueVmState disable(Integer hostId, Boolean start) {
        RescueVmState state = getState(hostId);
        if (!canDisable(state)) {
            throw new IllegalStateException("该虚拟机当前没有可恢复的救援状态");
        }
        Object lock = LOCKS.computeIfAbsent(hostId, ignored -> new Object());
        synchronized (lock) {
            state = getState(hostId);
            if (!canDisable(state)) {
                throw new IllegalStateException("该虚拟机当前没有可恢复的救援状态");
            }
            state.setStatus("DISABLING");
            state.setError(null);
            state.setUpdateTime(System.currentTimeMillis());
            rescueVmStateDao.updateById(state);
        }
        Boolean requestedStart = start;
        final Long stateId = state.getId();
        try {
            submit(() -> processDisable(stateId, hostId, requestedStart));
        } catch (IllegalStateException e) {
            markFailed(stateId, e.getMessage());
            throw e;
        }
        return state;
    }

    private void submit(Runnable action) {
        try {
            workflowExecutor.execute(action);
        } catch (RuntimeException e) {
            throw new IllegalStateException("救援任务提交失败，请稍后重试", e);
        }
    }

    private void markFailed(Long stateId, String message) {
        RescueVmState failed = rescueVmStateDao.selectById(stateId);
        if (failed == null) {
            return;
        }
        failed.setStatus("FAILED");
        failed.setError(message == null || message.isBlank() ? "救援任务提交失败" : message);
        failed.setUpdateTime(System.currentTimeMillis());
        rescueVmStateDao.updateById(failed);
    }

    private void processEnable(Long stateId, Integer hostId, Integer nodeId) {
        RescueVmState state = rescueVmStateDao.selectById(stateId);
        if (state == null) {
            return;
        }
        Master node = null;
        Vmhost vmhost = null;
        ProxmoxApiUtil api = new ProxmoxApiUtil();
        HashMap<String, String> cookie = null;
        boolean configCaptured = false;
        try {
                node = masterService.getById(nodeId);
                vmhost = vmhostService.getById(hostId);
                if (node == null || vmhost == null) {
                    throw new IllegalStateException("节点或虚拟机不存在");
                }
                cookie = masterService.getMasterCookieMap(node.getId());
                JSONObject config = requireConfig(api, node, cookie, vmhost.getVmid());
                JSONObject currentStatus = api.getVmStatus(node, cookie, vmhost.getVmid());
                String powerStatus = getStatus(currentStatus);
                String storage = resolveStorage(state.getStorage(), config, node);
                String rescueDevice = chooseRescueDevice(config);
                state.setStorage(storage);
                String currentIde2 = config.getString("ide2");
                // A previous rescue run may have detached a cloud-init volume
                // before this fix was deployed. Keep the recorded original so
                // the next exit can recreate it instead of losing the identity.
                if (isEmptyDriveSlot(currentIde2) && cloudInitStorage(state.getOriginalIde2()) != null) {
                    log.warn("[Rescue] preserving recorded cloud-init volume for recovery: hostId={}, vmid={}",
                            hostId, vmhost.getVmid());
                } else {
                    state.setOriginalIde2(currentIde2);
                }
                state.setOriginalBoot(config.getString("boot"));
                state.setOriginalRunning("running".equals(powerStatus) ? 1 : 0);
                configCaptured = true;
                state.setUpdateTime(System.currentTimeMillis());
                rescueVmStateDao.updateById(state);

                if (!"stopped".equals(powerStatus)) {
                    stopVm(api, node, cookie, vmhost.getVmid());
                }
                ensureIso(api, node, cookie, storage, state.getFileName(), state.getImageUrl());
                String isoVolume = storage + ":iso/" + state.getFileName();
                state.setIsoVolume(isoVolume);
                state.setUpdateTime(System.currentTimeMillis());
                rescueVmStateDao.updateById(state);
                api.resetVmConfig(node, cookie, vmhost.getVmid(), rescueDevice, isoVolume + ",media=cdrom");
                api.resetVmConfig(node, cookie, vmhost.getVmid(), "boot",
                        rescueBoot(state.getOriginalBoot(), rescueDevice));
                startVm(api, node, cookie, vmhost.getVmid());
                state.setStatus("ACTIVE");
                state.setError(null);
                state.setUpdateTime(System.currentTimeMillis());
                rescueVmStateDao.updateById(state);
                log.info("[Rescue] rescue system enabled: hostId={}, vmid={}, image={}",
                        hostId, vmhost.getVmid(), state.getImageId());
        } catch (Exception e) {
                String message = safeMessage(e);
                try {
                    if (cookie != null && node != null && vmhost != null
                            && configCaptured) {
                        restoreConfig(api, node, cookie, vmhost.getVmid(), state);
                        if (Integer.valueOf(1).equals(state.getOriginalRunning())) {
                            startVm(api, node, cookie, vmhost.getVmid());
                        }
                    }
                } catch (Exception recovery) {
                    message = message + "; 恢复原配置失败: " + safeMessage(recovery);
                }
                state.setStatus("FAILED");
                state.setError(message);
                state.setUpdateTime(System.currentTimeMillis());
                rescueVmStateDao.updateById(state);
                log.error("[Rescue] enable rescue system failed: hostId={}", hostId, e);
        }
    }

    private void processDisable(Long stateId, Integer hostId, Boolean requestedStart) {
        RescueVmState state = rescueVmStateDao.selectById(stateId);
        if (state == null) {
            return;
        }
        Master node = null;
        Vmhost vmhost = null;
        ProxmoxApiUtil api = new ProxmoxApiUtil();
        try {
                node = masterService.getById(state.getNodeId());
                vmhost = vmhostService.getById(hostId);
                if (node == null || vmhost == null) {
                    throw new IllegalStateException("节点或虚拟机不存在");
                }
                HashMap<String, String> cookie = masterService.getMasterCookieMap(node.getId());
                String currentStatus = getStatus(api.getVmStatus(node, cookie, vmhost.getVmid()));
                if (!"stopped".equals(currentStatus)) {
                    stopVm(api, node, cookie, vmhost.getVmid());
                }
                String restoreWarning = restoreConfig(api, node, cookie, vmhost.getVmid(), state);
                boolean start = requestedStart != null
                        ? requestedStart
                        : Integer.valueOf(1).equals(state.getOriginalRunning());
                if (start) {
                    startVm(api, node, cookie, vmhost.getVmid());
                }
                state.setStatus("DISABLED");
                state.setError(restoreWarning);
                state.setUpdateTime(System.currentTimeMillis());
                rescueVmStateDao.updateById(state);
                log.info("[Rescue] rescue system disabled: hostId={}, vmid={}", hostId, vmhost.getVmid());
        } catch (Exception e) {
                state.setStatus("FAILED");
                state.setError(safeMessage(e));
                state.setUpdateTime(System.currentTimeMillis());
                rescueVmStateDao.updateById(state);
                log.error("[Rescue] disable rescue system failed: hostId={}", hostId, e);
        }
    }

    private void ensureIso(ProxmoxApiUtil api, Master node, HashMap<String, String> cookie,
                           String storage, String fileName, String url) throws Exception {
        if (hasIso(api, node, cookie, storage, fileName)) {
            return;
        }
        HashMap<String, Object> params = new HashMap<>();
        params.put("content", "iso");
        params.put("filename", fileName);
        params.put("url", url);
        JSONObject response = api.postNodeApiForm(node, cookie,
                "/nodes/" + node.getNodeName() + "/storage/" + storage + "/download-url", params);
        String upid = response == null ? null : response.getString("data");
        if (upid != null && !upid.isBlank()) {
            waitForTask(api, node, cookie, upid, TASK_TIMEOUT_MILLIS);
        }
        if (!hasIso(api, node, cookie, storage, fileName)) {
            throw new IllegalStateException("PVE 未确认救援 ISO 已下载");
        }
    }

    private boolean hasIso(ProxmoxApiUtil api, Master node, HashMap<String, String> cookie,
                           String storage, String fileName) throws Exception {
        JSONObject response = api.getNodeApi(node, cookie,
                "/nodes/" + node.getNodeName() + "/storage/" + storage + "/content?content=iso",
                new HashMap<>());
        JSONArray data = response == null ? null : response.getJSONArray("data");
        if (data == null) {
            return false;
        }
        for (int i = 0; i < data.size(); i++) {
            JSONObject item = data.getJSONObject(i);
            if (item != null && (fileName.equals(item.getString("filename"))
                    || (storage + ":iso/" + fileName).equals(item.getString("volid")))) {
                return true;
            }
        }
        return false;
    }

    private void stopVm(ProxmoxApiUtil api, Master node, HashMap<String, String> cookie,
                        Integer vmid) throws Exception {
        JSONObject stop = api.forceStopVm(node, cookie, vmid);
        String upid = stop == null ? null : stop.getString("data");
        if (upid != null && !upid.isBlank()) {
            waitForTask(api, node, cookie, upid, STOP_TIMEOUT_MILLIS);
        }
        long deadline = System.currentTimeMillis() + STOP_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if ("stopped".equals(getStatus(api.getVmStatus(node, cookie, vmid)))) {
                return;
            }
            sleep(POLL_INTERVAL_MILLIS);
        }
        throw new IllegalStateException("等待虚拟机关机超时");
    }

    private void startVm(ProxmoxApiUtil api, Master node, HashMap<String, String> cookie,
                         Integer vmid) throws Exception {
        JSONObject start = api.postNodeApi(node, cookie,
                "/nodes/" + node.getNodeName() + "/qemu/" + vmid + "/status/start", new HashMap<>());
        String upid = start == null ? null : start.getString("data");
        if (upid != null && !upid.isBlank()) {
            waitForTask(api, node, cookie, upid, STOP_TIMEOUT_MILLIS);
        }
        long deadline = System.currentTimeMillis() + STOP_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if ("running".equals(getStatus(api.getVmStatus(node, cookie, vmid)))) {
                return;
            }
            sleep(POLL_INTERVAL_MILLIS);
        }
        throw new IllegalStateException("等待虚拟机启动超时");
    }

    private void waitForTask(ProxmoxApiUtil api, Master node, HashMap<String, String> cookie,
                             String upid, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        String path = "/nodes/" + node.getNodeName() + "/tasks/" + upid + "/status";
        while (System.currentTimeMillis() < deadline) {
            JSONObject response = api.getNodeApi(node, cookie, path, new HashMap<>());
            JSONObject data = response == null ? null : response.getJSONObject("data");
            if (data != null && "stopped".equals(data.getString("status"))) {
                if (!"OK".equals(data.getString("exitstatus"))) {
                    throw new IllegalStateException("PVE任务失败: " + data.getString("exitstatus"));
                }
                return;
            }
            sleep(POLL_INTERVAL_MILLIS);
        }
        throw new IllegalStateException("等待PVE任务完成超时");
    }

    private JSONObject requireConfig(ProxmoxApiUtil api, Master node, HashMap<String, String> cookie,
                                     Integer vmid) throws Exception {
        JSONObject response = api.getVmConfig(node, cookie, vmid);
        if (response == null) {
            throw new IllegalStateException("读取虚拟机配置失败");
        }
        return response;
    }

    /**
     * Restores the VM config without resurrecting a volume that PVE has already removed.
     * A missing original cloud-init volume is recoverable: recreate it on the original
     * storage and regenerate its contents before finishing the rescue exit workflow.
     */
    private String restoreConfig(ProxmoxApiUtil api, Master node, HashMap<String, String> cookie,
                                Integer vmid, RescueVmState state) throws Exception {
        detachRescueIso(api, node, cookie, vmid, state);
        boolean missingIde2Volume = false;
        String warning = null;
        if (state.getOriginalIde2() == null || state.getOriginalIde2().isBlank()) {
            api.deleteVmConfig(node, cookie, vmid, "ide2");
        } else {
            try {
                api.resetVmConfig(node, cookie, vmid, "ide2", state.getOriginalIde2());
            } catch (Exception e) {
                if (!isMissingLogicalVolume(e)) {
                    throw e;
                }
                String cloudInitStorage = cloudInitStorage(state.getOriginalIde2());
                if (cloudInitStorage != null) {
                    // PVE may remove the temporary cloud-init volume when ide2 is
                    // replaced by the rescue ISO. Recreate it on the original
                    // storage instead of deleting ide2 and losing cloud-init.
                    String recreated = cloudInitStorage + ":cloudinit,media=cdrom";
                    api.resetVmConfig(node, cookie, vmid, "ide2", recreated);
                    api.resetVmCloudinit(node, cookie, vmid);
                    warning = "原 cloud-init 卷不存在，已在存储 " + cloudInitStorage + " 重新创建";
                    log.warn("[Rescue] original cloud-init volume was missing and recreated: vmid={}, storage={}",
                            vmid, cloudInitStorage);
                } else {
                    api.deleteVmConfig(node, cookie, vmid, "ide2");
                    missingIde2Volume = true;
                    warning = "原 ide2 卷不存在，已删除失效挂载配置: " + state.getOriginalIde2();
                    log.warn("[Rescue] original ide2 volume is missing, stale config removed: vmid={}, volume={}",
                            vmid, state.getOriginalIde2());
                }
            }
        }
        String originalBoot = state.getOriginalBoot();
        if (missingIde2Volume) {
            originalBoot = removeBootDevice(originalBoot, "ide2");
        }
        if (originalBoot == null || originalBoot.isBlank()) {
            api.deleteVmConfig(node, cookie, vmid, "boot");
        } else {
            api.resetVmConfig(node, cookie, vmid, "boot", originalBoot);
        }
        return warning;
    }

    private void detachRescueIso(ProxmoxApiUtil api, Master node, HashMap<String, String> cookie,
                                 Integer vmid, RescueVmState state) throws Exception {
        String rescueVolume = state.getIsoVolume();
        if (rescueVolume == null || rescueVolume.isBlank()) {
            String storage = blankToNull(state.getStorage());
            String fileName = blankToNull(state.getFileName());
            if (storage != null && fileName != null) {
                rescueVolume = storage + ":iso/" + fileName;
            }
        }
        if (rescueVolume == null || rescueVolume.isBlank()) {
            return;
        }
        JSONObject config = api.getVmConfig(node, cookie, vmid);
        if (config == null) {
            throw new IllegalStateException("读取救援 ISO 挂载配置失败，暂不恢复虚拟机");
        }
        String rescueDevice = findRescueDevice(config, rescueVolume);
        if (rescueDevice != null) {
            api.deleteVmConfig(node, cookie, vmid, rescueDevice);
        }
    }

    private String findRescueDevice(JSONObject config, String rescueVolume) {
        if (config == null || rescueVolume == null || rescueVolume.isBlank()) {
            return null;
        }
        String expected = rescueVolume.split(",", 2)[0].trim();
        for (String key : config.keySet()) {
            if (!key.matches("(?:ide|sata)\\d+")) {
                continue;
            }
            String configured = config.getString(key);
            if (configured != null && expected.equals(configured.split(",", 2)[0].trim())) {
                return key;
            }
        }
        return null;
    }

    /**
     * Returns the storage name for a PVE cloud-init volume id, or null for a
     * normal ISO/disk. PVE commonly reports values such as
     * {@code nvmedisk:vm-463-cloudinit,media=cdrom}.
     */
    static String cloudInitStorage(String originalIde2) {
        if (originalIde2 == null || originalIde2.isBlank()) {
            return null;
        }
        String volumeId = originalIde2.split(",", 2)[0].trim();
        int separator = volumeId.indexOf(':');
        if (separator <= 0 || separator == volumeId.length() - 1) {
            return null;
        }
        String storage = volumeId.substring(0, separator).trim();
        String volume = volumeId.substring(separator + 1).trim();
        int slash = volume.lastIndexOf('/');
        String volumeName = slash >= 0 ? volume.substring(slash + 1) : volume;
        String normalized = volumeName.toLowerCase(Locale.ROOT);
        boolean cloudInit = "cloudinit".equals(normalized)
                || normalized.matches("vm-\\d+-cloudinit(?:\\.[a-z0-9]+)?");
        if (!cloudInit || !storage.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}")) {
            return null;
        }
        return storage;
    }

    private boolean isMissingLogicalVolume(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("no such logical volume")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    static String removeBootDevice(String originalBoot, String device) {
        if (originalBoot == null || originalBoot.isBlank()) {
            return null;
        }
        String order = originalBoot.trim();
        if (order.startsWith("order=")) {
            order = order.substring("order=".length());
        }
        List<String> devices = new ArrayList<>();
        for (String item : order.split(";")) {
            String trimmed = item.trim();
            if (!trimmed.isBlank() && !device.equals(trimmed)) {
                devices.add(trimmed);
            }
        }
        return devices.isEmpty() ? null : "order=" + String.join(";", devices);
    }

    static String rescueBoot(String originalBoot) {
        return rescueBoot(originalBoot, "ide2");
    }

    static String rescueBoot(String originalBoot, String rescueDevice) {
        String order = originalBoot == null ? "" : originalBoot.trim();
        if (order.startsWith("order=")) {
            order = order.substring("order=".length());
        }
        String bootDevice = rescueDevice == null || rescueDevice.isBlank() ? "ide2" : rescueDevice;
        List<String> devices = new ArrayList<>();
        for (String device : order.split(";")) {
            if (!device.isBlank() && !bootDevice.equals(device.trim())) {
                devices.add(device.trim());
            }
        }
        if (devices.isEmpty()) {
            devices.add("net0");
        }
        return "order=" + bootDevice + ";" + String.join(";", devices);
    }

    private String resolveStorage(String requested, JSONObject config, Master node) {
        String storage = blankToNull(requested);
        if (storage == null) {
            for (String disk : List.of("scsi0", "virtio0", "sata0", "ide0")) {
                String value = config.getString(disk);
                if (value != null && value.contains(":")) {
                    storage = value.substring(0, value.indexOf(':'));
                    break;
                }
            }
        }
        if (storage == null) {
            storage = blankToNull(node.getAutoStorage());
        }
        if (storage == null || !storage.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("无法确定可存放 ISO 的 PVE 存储，请传入合法 storage");
        }
        return storage;
    }

    private String chooseRescueDevice(JSONObject config) {
        // Keep an existing cloud-init drive (normally ide2) untouched. Prefer
        // the highest empty IDE slot, then fall back to an empty SATA slot.
        String[] candidates = {"ide3", "ide2", "ide1", "ide0",
                "sata5", "sata4", "sata3", "sata2", "sata1", "sata0"};
        for (String candidate : candidates) {
            String value = config == null ? null : config.getString(candidate);
            if (isEmptyDriveSlot(value)) {
                return candidate;
            }
        }
        throw new IllegalStateException("没有可用的磁盘槽位挂载救援 ISO，请先释放一个 IDE/SATA 槽位");
    }

    private boolean isEmptyDriveSlot(String value) {
        return value == null || value.isBlank() || "none".equalsIgnoreCase(value.trim());
    }

    private String getStatus(JSONObject response) {
        JSONObject data = response == null ? null : response.getJSONObject("data");
        String status = data == null ? null : data.getString("status");
        if (status == null || status.isBlank()) {
            throw new IllegalStateException("无法确认虚拟机电源状态");
        }
        return status;
    }

    private Vmhost requireVm(Integer hostId) {
        Vmhost vmhost = vmhostService.getById(hostId);
        if (vmhost == null || vmhost.getVmid() == null || vmhost.getNodeid() == null) {
            throw new IllegalArgumentException("虚拟机不存在或节点信息不完整");
        }
        return vmhost;
    }

    private Master requireNode(Integer nodeId) {
        Master node = masterService.getById(nodeId);
        if (node == null || node.getNodeName() == null || node.getNodeName().isBlank()) {
            throw new IllegalArgumentException("节点不存在或节点名称不完整");
        }
        return node;
    }

    private boolean isBusy(String status) {
        return "ENABLING".equals(status) || "ACTIVE".equals(status) || "DISABLING".equals(status);
    }

    private boolean canDisable(RescueVmState state) {
        if (state == null || state.getOriginalRunning() == null) {
            return false;
        }
        return "ACTIVE".equals(state.getStatus()) || "FAILED".equals(state.getStatus());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String safeMessage(Exception e) {
        return e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
    }

    private void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }
}
