package com.chuqiyun.proxmoxveams.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RescueServiceImplTest {
    @Test
    void rescueBootPutsCdromFirstAndKeepsOriginalOrder() {
        assertEquals("order=ide2;scsi0;net0", RescueServiceImpl.rescueBoot("order=scsi0;net0"));
        assertEquals("order=ide2;net0", RescueServiceImpl.rescueBoot(null));
        assertEquals("order=ide2;scsi0;net0", RescueServiceImpl.rescueBoot("order=ide2;scsi0;net0"));
        assertEquals("order=ide3;ide2;scsi0",
                RescueServiceImpl.rescueBoot("order=ide2;scsi0", "ide3"));
    }

    @Test
    void removesMissingCloudInitDiskFromBootOrder() {
        assertEquals("order=scsi0;net0",
                RescueServiceImpl.removeBootDevice("order=scsi0;ide2;net0", "ide2"));
        assertEquals(null,
                RescueServiceImpl.removeBootDevice("order=ide2", "ide2"));
    }

    @Test
    void recognizesCloudInitStorageWithoutTreatingRegularIsoAsCloudInit() {
        assertEquals("nvmedisk",
                RescueServiceImpl.cloudInitStorage("nvmedisk:vm-463-cloudinit,media=cdrom"));
        assertEquals("local-lvm",
                RescueServiceImpl.cloudInitStorage("local-lvm:vm-463-cloudinit.raw,media=cdrom"));
        assertEquals(null,
                RescueServiceImpl.cloudInitStorage("local:iso/rescue.iso,media=cdrom"));
        assertEquals(null,
                RescueServiceImpl.cloudInitStorage("bad storage:vm-463-cloudinit,media=cdrom"));
    }
}
