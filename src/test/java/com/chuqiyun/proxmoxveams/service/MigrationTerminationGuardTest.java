package com.chuqiyun.proxmoxveams.service;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.constant.TaskType;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Task;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MigrationTerminationGuardTest {
    private MasterService masterService;
    private VmhostService vmhostService;
    private ProxmoxApiUtil pve;
    private MigrationTerminationGuard guard;
    private Task task;
    private Map<Object, Object> params;
    private Master source;
    private Master target;

    @BeforeEach
    void setUp() {
        masterService = mock(MasterService.class);
        vmhostService = mock(VmhostService.class);
        pve = mock(ProxmoxApiUtil.class);
        guard = spy(new MigrationTerminationGuard());
        ReflectionTestUtils.setField(guard, "masterService", masterService);
        ReflectionTestUtils.setField(guard, "vmhostService", vmhostService);
        source = node(1, "source");
        target = node(2, "target");
        when(masterService.getById(1)).thenReturn(source);
        when(masterService.getById(2)).thenReturn(target);
        when(masterService.getMasterCookieMap(2)).thenReturn(new HashMap<>());
        Vmhost host = new Vmhost();
        host.setId(7);
        host.setNodeid(1);
        host.setVmid(101);
        when(vmhostService.getById(7)).thenReturn(host);
        task = new Task();
        task.setId(1158);
        task.setType(TaskType.MIGRATE_VM);
        task.setStatus(3);
        task.setHostid(7);
        params = new HashMap<>(Map.of("sourceNodeId", 1, "sourceVmid", 101, "targetNodeId", 2,
                "targetVmid", 126, "lastFailedStep", "RESTORE", "checkpointStage", "BACKUP"));
        doReturn(status("FAILED")).when(guard).requestStatus(any(Master.class), eq("1158"));
        doReturn(pve).when(guard).newProxmoxApi();
        when(pve.getNodeApi(eq(target), any(), eq("/cluster/resources"), any()))
                .thenReturn(JSONObject.parseObject("{\"data\":[{\"type\":\"node\",\"node\":\"target\"},{\"type\":\"qemu\",\"vmid\":102}]}"));
        when(pve.getNodeApi(eq(target), any(), eq("/nodes"), any()))
                .thenReturn(JSONObject.parseObject("{\"data\":[{\"node\":\"target\"},{\"node\":\"other-node\"}]}"));
    }

    private Master node(int id, String name) {
        Master node = new Master();
        node.setId(id);
        node.setNodeName(name);
        return node;
    }

    private JSONObject status(String state) {
        JSONObject result = new JSONObject();
        result.put("code", 200);
        result.put("data", new JSONObject(Map.of("status", state)));
        return result;
    }

    @Test
    void failedRestoreWithNoTargetPassesWithoutMutatingTaskOrParams() {
        Map<Object, Object> before = new HashMap<>(params);
        assertDoesNotThrow(() -> guard.verify(task, params));
        assertEquals(before, params);
        assertEquals(3, task.getStatus());
        verify(guard).requestStatus(source, "1158");
        verify(guard).requestStatus(target, "1158");
        verify(vmhostService, never()).updateById(any());
    }

    @Test
    void missingSourceStateAndRemovedSourceNodeAreBothSupported() {
        doReturn(status("NOT_FOUND")).when(guard).requestStatus(source, "1158");
        doReturn(status("SUCCESS")).when(guard).requestStatus(target, "1158");
        assertDoesNotThrow(() -> guard.verify(task, params));
        when(masterService.getById(1)).thenReturn(null);
        assertDoesNotThrow(() -> guard.verify(task, params));
    }

    @Test
    void onlyFailedBackupOrRestoreBeforeNetworkChangesCanTerminate() {
        for (String step : new String[]{"NETWORK_REBIND", "START_TARGET_VM", "CLEANUP_SOURCE", "RELEASE_SOURCE_NETWORK", "UNKNOWN"}) {
            params.put("lastFailedStep", step);
            assertThrows(IllegalStateException.class, () -> guard.verify(task, params));
        }
        params.put("lastFailedStep", "BACKUP");
        params.remove("checkpointStage");
        assertDoesNotThrow(() -> guard.verify(task, params));
        params.put("checkpointStage", "NETWORK_REBIND");
        assertThrows(IllegalStateException.class, () -> guard.verify(task, params));
    }

    @Test
    void committedOrUnrecoveredTargetResourcesPreventTermination() {
        params.put("targetCommitted", true);
        assertThrows(IllegalStateException.class, () -> guard.verify(task, params));
        params.put("targetCommitted", false);
        params.put("targetNetwork", new HashMap<>());
        assertThrows(IllegalStateException.class, () -> guard.verify(task, params));
        verify(guard, never()).requestStatus(any(), anyString());
    }

    @Test
    void wrongTaskStateOrChangedSourceLocationPreventsTermination() {
        task.setStatus(1);
        assertThrows(IllegalStateException.class, () -> guard.verify(task, params));
        task.setStatus(3);
        vmhostService.getById(7).setNodeid(2);
        assertThrows(IllegalStateException.class, () -> guard.verify(task, params));
        verify(guard, never()).requestStatus(any(), anyString());
    }

    @Test
    void runningUnknownAndUnreachableAgentStatusesNeverCountAsIdle() {
        for (Master node : new Master[]{source, target}) {
            for (String state : new String[]{"RUNNING", "QUEUED", "UNKNOWN"}) {
                doReturn(status(state)).when(guard).requestStatus(node, "1158");
                assertThrows(IllegalStateException.class, () -> guard.verify(task, params));
            }
            doReturn(null).when(guard).requestStatus(node, "1158");
            assertThrows(IllegalStateException.class, () -> guard.verify(task, params));
            doThrow(new IllegalStateException("offline")).when(guard).requestStatus(node, "1158");
            assertThrows(IllegalStateException.class, () -> guard.verify(task, params));
            doReturn(status("FAILED")).when(guard).requestStatus(node, "1158");
        }
    }

    @Test
    void existingQemuOrLxcVmidAcrossTargetClusterPreventsRelease() {
        for (String type : new String[]{"qemu", "lxc"}) {
            when(pve.getNodeApi(eq(target), any(), eq("/cluster/resources"), any()))
                    .thenReturn(JSONObject.parseObject("{\"data\":[{\"type\":\"" + type + "\",\"vmid\":126,\"node\":\"other-node\"}]}"));
            assertTrue(assertThrows(IllegalStateException.class, () -> guard.verify(task, params))
                    .getMessage().contains("126"));
        }
    }

    @Test
    void missingOrMalformedTargetResourceDataNeverCountsAsAbsent() {
        for (JSONObject response : new JSONObject[]{null, new JSONObject(),
                JSONObject.parseObject("{\"data\":[{\"type\":\"qemu\"}]}"),
                JSONObject.parseObject("{\"data\":[null]}")}) {
            when(pve.getNodeApi(eq(target), any(), eq("/cluster/resources"), any())).thenReturn(response);
            assertThrows(IllegalStateException.class, () -> guard.verify(task, params));
        }
        when(pve.getNodeApi(eq(target), any(), eq("/cluster/resources"), any()))
                .thenThrow(new IllegalStateException("unreachable"));
        assertThrows(IllegalStateException.class, () -> guard.verify(task, params));
    }

    @Test
    void missingTargetAndInvalidMigrationParametersPreventTermination() {
        when(masterService.getById(2)).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> guard.verify(task, params));
        when(masterService.getById(2)).thenReturn(target);
        params.put("targetVmid", null);
        assertThrows(IllegalStateException.class, () -> guard.verify(task, params));
    }

    @Test
    void missingMalformedOrMismatchedTargetClusterNodesPreventTermination() {
        for (JSONObject response : new JSONObject[]{null, new JSONObject(),
                JSONObject.parseObject("{\"data\":[]}"),
                JSONObject.parseObject("{\"data\":[{\"node\":\"unrelated-cluster\"}]}"),
                JSONObject.parseObject("{\"data\":[{\"node\":\"target\"},{}]}")}) {
            when(pve.getNodeApi(eq(target), any(), eq("/nodes"), any())).thenReturn(response);
            assertThrows(IllegalStateException.class, () -> guard.verify(task, params));
        }
    }
}
