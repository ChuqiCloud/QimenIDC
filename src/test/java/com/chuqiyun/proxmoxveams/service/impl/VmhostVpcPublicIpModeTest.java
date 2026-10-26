package com.chuqiyun.proxmoxveams.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VmhostVpcPublicIpModeTest {
    @Test
    void configuredInterfaceUpgradesLegacyModeToAddressAlias() {
        VmhostServiceImpl service = new VmhostServiceImpl();

        assertEquals("l2_alias", ReflectionTestUtils.invokeMethod(
                service, "normalizeVpcPublicIpMode", "legacy", "vmbr0"));
        assertEquals("l2_alias", ReflectionTestUtils.invokeMethod(
                service, "normalizeVpcPublicIpMode", "auto", "vmbr0"));
    }

    @Test
    void nodesWithoutInterfaceKeepLegacyNatMode() {
        VmhostServiceImpl service = new VmhostServiceImpl();

        assertEquals("legacy", ReflectionTestUtils.invokeMethod(
                service, "normalizeVpcPublicIpMode", "legacy", null));
        assertEquals("legacy", ReflectionTestUtils.invokeMethod(
                service, "normalizeVpcPublicIpMode", "auto", ""));
    }

    @Test
    void explicitNonLegacyModesRemainUnchanged() {
        VmhostServiceImpl service = new VmhostServiceImpl();

        assertEquals("routed", ReflectionTestUtils.invokeMethod(
                service, "normalizeVpcPublicIpMode", "routed", "vmbr0"));
        assertEquals("proxy_arp", ReflectionTestUtils.invokeMethod(
                service, "normalizeVpcPublicIpMode", "proxy_arp", "vmbr0"));
    }
}
