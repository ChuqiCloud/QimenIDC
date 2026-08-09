package com.chuqiyun.proxmoxveams.utils;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CloudInitNetworkUtilTest {
    @Test
    void normalizesFirewallCidrsByIpVersion() {
        assertEquals("107.178.112.104/32", CloudInitNetworkUtil.normalizeFirewallCidr("107.178.112.104/29"));
        assertEquals("107.178.112.107/32", CloudInitNetworkUtil.normalizeFirewallCidr("107.178.112.107"));
        assertEquals("2604:6280:19f:7d::/96", CloudInitNetworkUtil.normalizeFirewallCidr("2604:6280:19f:7d::/96"));
        assertEquals("2604:6280:19f:7d::/128", CloudInitNetworkUtil.normalizeFirewallCidr("2604:6280:19f:7d::"));
    }

    @Test
    void preservesIpv6SegmentWhenBareIpAndSegmentBothExist() {
        List<String> cidrList = CloudInitNetworkUtil.normalizeFirewallCidrs(Arrays.asList(
                "2604:6280:19f:7d::",
                "2604:6280:19f:7d::/96"
        ));

        assertEquals(List.of("2604:6280:19f:7d::/96"), cidrList);
    }

    @Test
    void buildsFirewallCidrsFromCloudInitConfig() {
        Map<String, String> ipConfig = new LinkedHashMap<>();
        ipConfig.put("0", "ip=107.178.112.104/29,gw=107.178.112.105,ip6=2604:6280:19f:7d::/96,gw6=2604:6280:19f:7d::1");

        assertEquals(List.of("107.178.112.104/32", "2604:6280:19f:7d::/96"),
                CloudInitNetworkUtil.getFirewallCidrList(ipConfig));
    }
}
