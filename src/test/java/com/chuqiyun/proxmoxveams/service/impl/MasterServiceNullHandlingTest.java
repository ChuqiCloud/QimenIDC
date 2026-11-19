package com.chuqiyun.proxmoxveams.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class MasterServiceNullHandlingTest {
    @Test
    void vmInfoAndStatusReturnNullForIncompleteIdentifiers() {
        MasterServiceImpl service = new MasterServiceImpl();

        assertNull(service.getVmInfo(null, 982));
        assertNull(service.getVmInfo(13, null));
        assertNull(service.getVmStatusCurrent(null, 982));
        assertNull(service.getVmStatusCurrent(13, null));
    }
}
