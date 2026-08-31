package com.chuqiyun.proxmoxveams.utils;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VmDiskUsageUtilTest {

    @Test
    void buildsSystemDiskRequestWithoutDataDisks() {
        JSONObject config = new JSONObject();
        config.put("scsi0", "local-lvm:vm-100-disk-0,discard=on,size=40G");
        config.put("ide2", "local-lvm:cloudinit,media=cdrom");

        JSONObject request = VmDiskUsageUtil.buildRequest(config);
        JSONArray disks = request.getJSONArray("disks");

        assertEquals("scsi0", request.getString("system_device"));
        assertEquals(1, disks.size());
        assertEquals("local-lvm:vm-100-disk-0", disks.getJSONObject(0).getString("volume"));
        assertEquals(40L * 1024 * 1024 * 1024, disks.getJSONObject(0).getLongValue("provisioned_bytes"));
    }

    @Test
    void includesAndSortsMultipleDataDisks() {
        JSONObject config = new JSONObject();
        config.put("virtio0", "ceph:vm-101-disk-2,size=8G");
        config.put("scsi2", "local-zfs:vm-101-disk-1,size=1.5T");
        config.put("scsi0", "local-lvm:vm-101-disk-0,size=64G");

        JSONObject request = VmDiskUsageUtil.buildRequest(config);
        JSONArray disks = request.getJSONArray("disks");

        assertEquals("scsi0", request.getString("system_device"));
        assertEquals(3, disks.size());
        assertEquals("scsi0", disks.getJSONObject(0).getString("device"));
        assertEquals("scsi2", disks.getJSONObject(1).getString("device"));
        assertEquals("virtio0", disks.getJSONObject(2).getString("device"));
        assertEquals(1649267441664L, disks.getJSONObject(1).getLongValue("provisioned_bytes"));
    }

    @Test
    void usesBootOrderWhenScsiZeroDoesNotExist() {
        JSONObject config = new JSONObject();
        config.put("boot", "order=virtio1;ide2;net0");
        config.put("virtio1", "local:100/vm-100-disk-0.qcow2,size=20G");
        config.put("ide2", "none,media=cdrom");

        JSONObject request = VmDiskUsageUtil.buildRequest(config);

        assertEquals("virtio1", request.getString("system_device"));
        assertEquals(1, request.getJSONArray("disks").size());
    }

    @Test
    void bootOrderTakesPriorityOverScsiZero() {
        JSONObject config = new JSONObject();
        config.put("boot", "order=scsi1;scsi0");
        config.put("scsi0", "local-lvm:vm-102-disk-0,size=20G");
        config.put("scsi1", "local-lvm:vm-102-disk-1,size=40G");

        JSONObject request = VmDiskUsageUtil.buildRequest(config);

        assertEquals("scsi1", request.getString("system_device"));
    }

    @Test
    void returnsEmptyRequestForMissingConfig() {
        JSONObject request = VmDiskUsageUtil.buildRequest(null);

        assertNull(request.get("system_device"));
        assertEquals(0, request.getJSONArray("disks").size());
    }
}
