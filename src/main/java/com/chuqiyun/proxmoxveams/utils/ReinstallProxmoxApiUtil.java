package com.chuqiyun.proxmoxveams.utils;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.entity.Master;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Waits for shutdown before reinstall configuration changes and recovers failed disk detachment.
 */
public class ReinstallProxmoxApiUtil extends ProxmoxApiUtil {
    private static final Logger LOG = LoggerFactory.getLogger(ReinstallProxmoxApiUtil.class);
    private static final Object[] DISK_LOCKS = new Object[64];

    static {
        for (int index = 0; index < DISK_LOCKS.length; index++) {
            DISK_LOCKS[index] = new Object();
        }
    }

    @Override
    public void resetVmUsername(Master node, HashMap<String, String> cookie,
            Integer vmid, String username) throws UnauthorizedException {
        ensureStopped(node, cookie, vmid);
        super.resetVmUsername(node, cookie, vmid, username);
    }

    @Override
    public void deleteVmDisk(Master node, HashMap<String, String> cookie,
            Integer vmid, String disk) throws UnauthorizedException {
        String key = node.getNodeName() + "/" + vmid;
        synchronized (DISK_LOCKS[Math.floorMod(key.hashCode(), DISK_LOCKS.length)]) {
            JSONObject original = requireConfig(node, cookie, vmid);
            String originalDisk = original.getString(disk);
            if (originalDisk == null || originalDisk.isBlank()) {
                throw new IllegalStateException("Reinstall disk is not attached: " + disk
                        + ". Restore the existing disk before retrying.");
            }
            ensureStopped(node, cookie, vmid);
            JSONObject stoppedConfig = requireConfig(node, cookie, vmid);
            if (!originalDisk.equals(stoppedConfig.getString(disk))) {
                throw new IllegalStateException("Disk configuration changed while stopping VM " + vmid);
            }
            try {
                deleteStoppedDisk(node, cookie, vmid, disk, originalDisk, stoppedConfig);
            } catch (RuntimeException failure) {
                try {
                    restoreDisk(node, cookie, vmid, disk, originalDisk, stoppedConfig.getString("boot"));
                } catch (RuntimeException recoveryFailure) {
                    failure.addSuppressed(recoveryFailure);
                    throw new IllegalStateException("Reinstall disk operation failed and recovery"
                            + " could not be confirmed. Inspect VM " + vmid + " before retrying.", failure);
                }
                throw failure;
            }
        }
    }

    private void deleteStoppedDisk(Master node, HashMap<String, String> cookie, Integer vmid,
            String disk, String originalDisk, JSONObject config) throws UnauthorizedException {
        String path = "/nodes/" + node.getNodeName() + "/qemu/" + vmid + "/config";
        HashMap<String, Object> detach = new HashMap<>();
        detach.put("delete", disk);
        detach.put("digest", config.getString("digest"));
        putNodeApi(node, cookie, path, detach);
        JSONObject detached = requireConfig(node, cookie, vmid);
        for (String option : detached.keySet()) {
            if (option.matches("unused\\d+") && sameVolume(detached.getString(option), originalDisk)) {
                HashMap<String, Object> remove = new HashMap<>();
                remove.put("delete", option);
                remove.put("digest", detached.getString("digest"));
                putNodeApi(node, cookie, path, remove);
                return;
            }
        }
        throw new IllegalStateException("Disk detachment was not confirmed for VM " + vmid);
    }

    protected long stopTimeoutNanos() {
        return TimeUnit.MINUTES.toNanos(5);
    }

    protected void awaitNextStatus() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for VM shutdown", interrupted);
        }
    }

    private void ensureStopped(Master node, HashMap<String, String> cookie,
            Integer vmid) throws UnauthorizedException {
        if ("stopped".equals(powerStatus(node, cookie, vmid))) {
            return;
        }
        LOG.info("[ReinstallDiskGuard] Waiting for shutdown before disk changes: node={} vmid={}",
                node.getNodeName(), vmid);
        JSONObject response;
        try {
            response = forceStopVm(node, cookie, vmid);
        } catch (RuntimeException failure) {
            if ("stopped".equals(powerStatus(node, cookie, vmid))) {
                return;
            }
            throw failure;
        }
        String upid = response == null ? null : response.getString("data");
        if (upid == null || !upid.startsWith("UPID:")) {
            throw new IllegalStateException("Shutdown task was not acknowledged for VM " + vmid);
        }
        // RestTemplate encodes this path when constructing the request URI.
        String taskPath = "/nodes/" + node.getNodeName() + "/tasks/" + upid + "/status";
        long started = System.nanoTime();
        do {
            JSONObject taskResponse = getNodeApi(node, cookie, taskPath, new HashMap<>());
            JSONObject task = taskResponse == null ? null : taskResponse.getJSONObject("data");
            if (task == null || task.getString("status") == null) {
                throw new IllegalStateException("Unable to confirm shutdown task for VM " + vmid);
            }
            if ("stopped".equals(task.getString("status"))) {
                if (!"OK".equals(task.getString("exitstatus"))) {
                    String exitStatus = task.getString("exitstatus");
                    if (exitStatus != null && exitStatus.contains("can't lock file")) {
                        if ("stopped".equals(powerStatus(node, cookie, vmid))) {
                            return;
                        }
                        awaitNextStatus();
                        JSONObject retry = forceStopVm(node, cookie, vmid);
                        upid = retry == null ? null : retry.getString("data");
                        if (upid == null || !upid.startsWith("UPID:")) {
                            throw new IllegalStateException("Shutdown retry was not acknowledged for VM " + vmid);
                        }
                        taskPath = "/nodes/" + node.getNodeName() + "/tasks/" + upid + "/status";
                        continue;
                    }
                    throw new IllegalStateException("Shutdown task failed for VM " + vmid);
                }
                if (!"stopped".equals(powerStatus(node, cookie, vmid))) {
                    throw new IllegalStateException("VM restarted before disk changes: " + vmid);
                }
                LOG.info("[ReinstallDiskGuard] Shutdown completed: node={} vmid={}",
                        node.getNodeName(), vmid);
                return;
            }
            awaitNextStatus();
        } while (System.nanoTime() - started < stopTimeoutNanos());
        throw new IllegalStateException("Shutdown timed out before disk changes for VM " + vmid);
    }

    private String powerStatus(Master node, HashMap<String, String> cookie,
            Integer vmid) throws UnauthorizedException {
        JSONObject response = getVmStatus(node, cookie, vmid);
        JSONObject data = response == null ? null : response.getJSONObject("data");
        String status = data == null ? null : data.getString("status");
        if (status == null || status.isBlank()) {
            throw new IllegalStateException("Unable to confirm power status for VM " + vmid);
        }
        return status;
    }

    private JSONObject requireConfig(Master node, HashMap<String, String> cookie,
            Integer vmid) throws UnauthorizedException {
        JSONObject config = getVmConfig(node, cookie, vmid);
        if (config == null || config.getString("digest") == null) {
            throw new IllegalStateException("Unable to read VM configuration for " + vmid);
        }
        return config;
    }

    private void restoreDisk(Master node, HashMap<String, String> cookie, Integer vmid,
            String disk, String originalDisk, String originalBoot) throws UnauthorizedException {
        String configPath = "/nodes/" + node.getNodeName() + "/qemu/" + vmid + "/config";
        JSONObject current = requireConfig(node, cookie, vmid);
        String attached = current.getString(disk);
        if (attached != null && !sameVolume(attached, originalDisk)) {
            throw new IllegalStateException("Disk attachment changed concurrently for VM " + vmid);
        }

        // An unsuccessful hot-unplug can still leave a pending delete in PVE.
        HashMap<String, Object> revert = new HashMap<>();
        revert.put("revert", disk);
        revert.put("digest", current.getString("digest"));
        putNodeApi(node, cookie, configPath, revert);
        current = requireConfig(node, cookie, vmid);
        attached = current.getString(disk);
        if (attached != null && !sameVolume(attached, originalDisk)) {
            throw new IllegalStateException("Disk attachment changed during recovery for VM " + vmid);
        }
        if (attached == null) {
            boolean originalExists = false;
            for (String option : current.keySet()) {
                if (option.matches("unused\\d+") && sameVolume(current.getString(option), originalDisk)) {
                    originalExists = true;
                    break;
                }
            }
            if (!originalExists) {
                throw new IllegalStateException("Original disk is unavailable for recovery on VM " + vmid);
            }
        }
        HashMap<String, Object> restore = new HashMap<>();
        restore.put("digest", current.getString("digest"));
        restore.put(disk, originalDisk);
        if (originalBoot != null) {
            restore.put("boot", originalBoot);
        }
        putNodeApi(node, cookie, configPath, restore);
        JSONObject restored = requireConfig(node, cookie, vmid);
        if (!originalDisk.equals(restored.getString(disk))
                || (originalBoot != null && !originalBoot.equals(restored.getString("boot")))) {
            throw new IllegalStateException("Disk recovery could not be verified for VM " + vmid);
        }
        LOG.warn("[ReinstallDiskGuard] Restored disk configuration after failure: node={} vmid={} disk={}",
                node.getNodeName(), vmid, disk);
    }

    private static boolean sameVolume(String first, String second) {
        return first != null && second != null
                && first.split(",", 2)[0].equals(second.split(",", 2)[0]);
    }
}
