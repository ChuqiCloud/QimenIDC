package com.chuqiyun.proxmoxveams.utils;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.entity.Master;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReinstallProxmoxApiUtilTest {
    private static final int VMID = 101;
    private static final String DISK = "local-lvm:vm-101-disk-0,discard=on,size=20G";
    private static final String BOOT = "order=scsi0;ide2;net0";
    private final HashMap<String, String> cookie = new HashMap<>();
    private Master node;
    private Fixture api;

    @BeforeEach
    void setUp() {
        node = new Master();
        node.setNodeName("fixture");
        api = new Fixture();
    }

    @Test
    void waitsForSuccessfulShutdownBeforeDeletingDisk() {
        api.taskPollsBeforeStop = 3;

        deleteDisk();

        assertTrue(api.events.indexOf("shutdown-complete") < api.events.indexOf("detach"));
        assertFalse(api.config.containsKey("scsi0"));
        assertFalse(api.config.containsKey("unused0"));
        assertEquals(1, api.stopRequests);
    }

    @Test
    void deletesOnlyMatchingUnusedVolumeWhenAlreadyStopped() {
        api.power = "stopped";
        api.unusedSlot = "unused2";
        api.config.put("unused0", "local-lvm:vm-101-disk-9");

        deleteDisk();

        assertEquals(0, api.stopRequests);
        assertEquals(List.of("detach", "remove"), api.events);
        assertEquals("local-lvm:vm-101-disk-9", api.config.getString("unused0"));
        assertFalse(api.config.containsKey("unused2"));
    }

    @Test
    void timeoutLeavesDiskAndBootUntouched() {
        api.taskPollsBeforeStop = Integer.MAX_VALUE;
        api.timeoutNanos = 0;

        expectFailure("timed out", this::deleteDisk);

        assertOriginal();
        assertEquals(0, api.writes);
    }

    @Test
    void failedStopPreventsDiskChanges() {
        api.stopTaskFails = true;

        expectFailure("Shutdown task failed", this::deleteDisk);

        assertOriginal();
        assertEquals(0, api.writes);
    }

    @Test
    void retriesTransientStopLockFailure() {
        api.stopLockFailures = 1;

        deleteDisk();

        assertEquals(2, api.stopRequests);
        assertTrue(api.events.indexOf("shutdown-complete") < api.events.indexOf("detach"));
    }

    @Test
    void unknownPowerStatusPreventsDiskChanges() {
        api.power = null;

        expectFailure("power status", this::deleteDisk);

        assertOriginal();
        assertEquals(0, api.writes);
    }

    @Test
    void missingStopAcknowledgementPreventsDiskChanges() {
        api.missingUpid = true;

        expectFailure("not acknowledged", this::deleteDisk);

        assertOriginal();
        assertEquals(0, api.writes);
    }

    @Test
    void hotplugFailureClearsPendingDeleteBeforeLaterStop() {
        api.power = "stopped";
        api.hotplugRace = true;

        expectFailure("hotplug problem", this::deleteDisk);

        assertFalse(api.pendingDelete);
        api.applyPendingStop();
        assertOriginal();
        assertTrue(api.events.contains("revert"));
    }

    @Test
    void failedRemovalReattachesOriginalDiskAndBoot() {
        api.power = "stopped";
        api.removeFails = true;

        expectFailure("remove failed", this::deleteDisk);

        assertOriginal();
        assertFalse(api.config.containsKey(api.unusedSlot));
    }

    @Test
    void preservesConcurrentReplacementAndReportsRecoveryFailure() {
        api.power = "stopped";
        api.replaceDuringRemove = true;

        IllegalStateException failure = expectFailure("recovery could not be confirmed", this::deleteDisk);

        assertTrue(api.config.getString("scsi0").startsWith("local-lvm:other"));
        assertFalse(api.events.contains("restore"));
        assertEquals(1, failure.getCause().getSuppressed().length);
    }

    @Test
    void refusesDetachedDiskWithoutMutation() {
        api.config.remove("scsi0");
        api.config.put("unused0", DISK);

        expectFailure("not attached", this::deleteDisk);

        assertEquals(0, api.writes);
        assertEquals(0, api.stopRequests);
        assertEquals(DISK, api.config.getString("unused0"));
    }

    @Test
    void waitsBeforeFirstReinstallUsernameUpdate() {
        api.resetVmUsername(node, cookie, VMID, "root");

        assertTrue(api.events.indexOf("shutdown-complete") < api.events.indexOf("username"));
    }

    @Test
    void timeoutPreventsFirstReinstallConfigurationUpdate() {
        api.taskPollsBeforeStop = Integer.MAX_VALUE;
        api.timeoutNanos = 0;

        expectFailure("timed out", () -> api.resetVmUsername(node, cookie, VMID, "root"));

        assertEquals(0, api.writes);
    }

    @Test
    void digestConflictPreventsDetachingChangedConfiguration() {
        api.power = "stopped";
        api.conflictBeforeDetach = true;

        expectFailure("digest conflict", this::deleteDisk);

        assertOriginal();
        assertFalse(api.events.contains("detach"));
    }

    @Test
    void digestConflictRecoveryPreservesConcurrentBootOrder() {
        api.power = "stopped";
        api.conflictBeforeDetach = true;
        api.changeBootBeforeDetach = true;

        expectFailure("digest conflict", this::deleteDisk);

        assertEquals("order=virtio0;net0", api.config.getString("boot"));
        assertTrue(api.events.contains("revert"));
        assertFalse(api.events.contains("restore"));
    }

    @Test
    void interruptedWaitPreservesDiskAndInterruptFlag() {
        api.interruptWait = true;
        try {
            expectFailure("Interrupted", this::deleteDisk);

            assertTrue(Thread.currentThread().isInterrupted());
            assertOriginal();
            assertEquals(0, api.writes);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void unreadableTaskStatusPreventsDiskChanges() {
        api.unreadableTask = true;

        expectFailure("confirm shutdown task", this::deleteDisk);

        assertOriginal();
        assertEquals(0, api.writes);
    }

    @Test
    void detectsDiskReplacementDuringShutdownBeforeAnyWrite() {
        api.replaceDuringStop = true;

        expectFailure("changed while stopping", this::deleteDisk);

        assertEquals("local-lvm:other,size=30G", api.config.getString("scsi0"));
        assertEquals(0, api.writes);
    }

    private void deleteDisk() {
        api.deleteVmDisk(node, cookie, VMID, "scsi0");
    }

    private void assertOriginal() {
        assertEquals(DISK, api.config.getString("scsi0"));
        assertEquals(BOOT, api.config.getString("boot"));
    }

    private IllegalStateException expectFailure(String message, Runnable action) {
        IllegalStateException failure = assertThrows(IllegalStateException.class, action::run);
        assertTrue(failure.getMessage().contains(message), failure.toString());
        return failure;
    }

    private static JSONObject envelope(JSONObject data) {
        JSONObject response = new JSONObject();
        response.put("data", data);
        return response;
    }

    private static class Fixture extends ReinstallProxmoxApiUtil {
        final JSONObject config = new JSONObject();
        final List<String> events = new ArrayList<>();
        String power = "running";
        String unusedSlot = "unused0";
        long timeoutNanos = TimeUnit.SECONDS.toNanos(5);
        int stopRequests;
        int writes;
        int taskPolls;
        int taskPollsBeforeStop = 2;
        int version;
        int stopLockFailures;
        boolean stopTaskFails;
        boolean missingUpid;
        boolean hotplugRace;
        boolean removeFails;
        boolean replaceDuringRemove;
        boolean replaceDuringStop;
        boolean conflictBeforeDetach;
        boolean changeBootBeforeDetach;
        boolean pendingDelete;
        boolean interruptWait;
        boolean unreadableTask;

        Fixture() {
            config.put("scsi0", DISK);
            config.put("boot", BOOT);
            config.put("digest", "0");
        }

        @Override
        protected long stopTimeoutNanos() {
            return timeoutNanos;
        }

        @Override
        protected void awaitNextStatus() {
            if (interruptWait) {
                Thread.currentThread().interrupt();
                super.awaitNextStatus();
            }
        }

        @Override
        public JSONObject getVmStatus(Master node, HashMap<String, String> cookie, Integer vmid) {
            JSONObject data = new JSONObject();
            data.put("status", power);
            return envelope(data);
        }

        @Override
        public JSONObject forceStopVm(Master node, HashMap<String, String> cookie, Integer vmid) {
            stopRequests++;
            JSONObject response = new JSONObject();
            if (!missingUpid) {
                response.put("data", "UPID:fixture:shutdown");
            }
            return response;
        }

        @Override
        public JSONObject getNodeApi(Master node, HashMap<String, String> cookie,
                String path, HashMap<String, Object> params) {
            assertEquals("/nodes/fixture/tasks/UPID:fixture:shutdown/status", path);
            taskPolls++;
            if (unreadableTask) {
                return new JSONObject();
            }
            JSONObject task = new JSONObject();
            if (stopLockFailures > 0) {
                stopLockFailures--;
                task.put("status", "stopped");
                task.put("exitstatus", "can't lock file '/fixture/lock' - got timeout");
                return envelope(task);
            }
            if (taskPolls >= taskPollsBeforeStop) {
                task.put("status", "stopped");
                task.put("exitstatus", stopTaskFails ? "ERROR" : "OK");
                power = "stopped";
                events.add("shutdown-complete");
                if (replaceDuringStop) {
                    config.put("scsi0", "local-lvm:other,size=30G");
                    bumpVersion();
                }
            } else {
                task.put("status", "running");
            }
            return envelope(task);
        }

        @Override
        public JSONObject getVmConfig(Master node, HashMap<String, String> cookie, Integer vmid) {
            return JSONObject.parseObject(config.toJSONString());
        }

        @Override
        public JSONObject putNodeApi(Master node, HashMap<String, String> cookie,
                String path, HashMap<String, Object> params) {
            assertEquals("/nodes/fixture/qemu/" + VMID + "/config", path);
            writes++;
            if (conflictBeforeDetach && "scsi0".equals(params.get("delete"))) {
                conflictBeforeDetach = false;
                if (changeBootBeforeDetach) {
                    config.put("boot", "order=virtio0;net0");
                }
                bumpVersion();
            }
            if (params.containsKey("digest")) {
                assertNotNull(params.get("digest"));
                if (!params.get("digest").equals(config.getString("digest"))) {
                    throw new IllegalStateException("digest conflict");
                }
            }
            if (params.containsKey("revert")) {
                assertEquals("scsi0", params.get("revert"));
                pendingDelete = false;
                events.add("revert");
            } else if ("scsi0".equals(params.get("delete"))) {
                assertNotNull(params.get("digest"));
                if (hotplugRace) {
                    power = "running";
                    pendingDelete = true;
                    throw new IllegalStateException("hotplug problem - can't unplug bootdisk 'scsi0'");
                }
                assertEquals("stopped", power);
                config.put(unusedSlot, config.remove("scsi0"));
                config.put("boot", "order=ide2;net0");
                events.add("detach");
            } else if (unusedSlot.equals(params.get("delete"))) {
                assertNotNull(params.get("digest"));
                if (replaceDuringRemove) {
                    config.put("scsi0", "local-lvm:other,size=30G");
                    bumpVersion();
                    throw new IllegalStateException("concurrent replacement");
                }
                if (removeFails) {
                    throw new IllegalStateException("remove failed");
                }
                config.remove(unusedSlot);
                events.add("remove");
            } else if (params.containsKey("scsi0")) {
                assertNotNull(params.get("digest"));
                config.put("scsi0", params.get("scsi0"));
                config.remove(unusedSlot);
                if (params.containsKey("boot")) {
                    config.put("boot", params.get("boot"));
                }
                events.add("restore");
            } else if (params.containsKey("ciuser")) {
                assertEquals("stopped", power);
                events.add("username");
            } else {
                fail("Unexpected configuration request " + params.keySet());
            }
            bumpVersion();
            return new JSONObject();
        }

        void bumpVersion() {
            config.put("digest", Integer.toString(++version));
        }

        void applyPendingStop() {
            power = "stopped";
            if (pendingDelete) {
                config.put(unusedSlot, config.remove("scsi0"));
            }
        }
    }
}
