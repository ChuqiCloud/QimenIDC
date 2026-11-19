package com.chuqiyun.proxmoxveams.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RescueServiceImplTest {
    @Test
    void rescueBootPutsCdromFirstAndKeepsOriginalOrder() {
        assertEquals("order=ide2;scsi0;net0", RescueServiceImpl.rescueBoot("order=scsi0;net0"));
        assertEquals("order=ide2;net0", RescueServiceImpl.rescueBoot(null));
        assertEquals("order=ide2;scsi0;net0", RescueServiceImpl.rescueBoot("order=ide2;scsi0;net0"));
    }
}
