package com.chuqiyun.proxmoxveams.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RescueImageCatalogTest {
    @Test
    void exposesOnlyThePinnedRescueImages() {
        List<Map<String, Object>> images = RescueImageCatalog.asMaps();

        assertEquals(2, images.size());
        assertEquals("https://mirror.chuqiyun.com/iso/WePE_64_V2.3.iso",
                RescueImageCatalog.get(RescueImageCatalog.WINDOWS_ID).url());
        assertEquals("systemrescue-13.03-amd64.iso",
                RescueImageCatalog.get(RescueImageCatalog.LINUX_ID).fileName());
        assertNotNull(RescueImageCatalog.get(RescueImageCatalog.WINDOWS_ID));
    }
}
