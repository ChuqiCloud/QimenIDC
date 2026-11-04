package com.chuqiyun.proxmoxveams.utils;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @Author: 星禾
 * @Description: VNC 实际出口 IP/CIDR 解析边界验证
 * @DateTime: 2026/11/4 10:22
 */
class VncSourceNetworksTest {
    @Test
    void acceptsMixedSeparatorsAndCanonicalizesNetworksWithoutDns() throws Exception {
        assertEquals(List.of("192.0.2.0/24", "198.51.100.8/32",
                        InetAddress.getByName("2001:db8:1::").getHostAddress() + "/64"),
                VncSourceNetworks.parse("198.51.100.8,192.0.2.99/24\n2001:db8:1::1234/64\t192.0.2.1/24"));
    }

    @Test
    void rejectsInvalidAndAllowAllSources() {
        for (String invalid : List.of("example.com", "https://192.0.2.1", "192.0.2.1:7600",
                "999.1.1.1", "192.0.2", "[2001:db8::1]", "fe80::1%eth0", "2001:db8::g",
                "192.0.2.1/", "192.0.2.1/-1", "192.0.2.1/33", "2001:db8::1/129", "192.0.2.1/1/2",
                "0.0.0.0", "::", "224.0.0.1", "ff02::1", "0.0.0.0/0", "::/0")) {
            assertThrows(IllegalArgumentException.class, () -> VncSourceNetworks.parse(invalid), invalid);
        }
        assertEquals(List.of(), VncSourceNetworks.parse(" \n\t"));
        assertEquals(List.of(), VncSourceNetworks.parse(null));
    }
}
