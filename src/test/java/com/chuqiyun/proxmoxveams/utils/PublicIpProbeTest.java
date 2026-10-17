package com.chuqiyun.proxmoxveams.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicIpProbeTest {

    @Test
    void textSourcesAreParsedFromPlainBodies() {
        assertEquals("203.0.113.7", PublicIpProbe.sanitize("203.0.113.7"));
        assertEquals("203.0.113.7", PublicIpProbe.sanitize("203.0.113.7\n"));
        assertEquals("203.0.113.7", PublicIpProbe.sanitize("<html>203.0.113.7</html>"));
        assertEquals("203.0.113.7", PublicIpProbe.sanitize("{\"ip\":\"203.0.113.7\"}"));
    }

    @Test
    void ipipChineseSentenceIsParsed() {
        assertEquals("159.75.12.57",
                PublicIpProbe.sanitize("当前 IP：159.75.12.57  来自于：中国 广东 广州  电信"));
        assertEquals("159.75.12.57",
                PublicIpProbe.sanitize("当前 IP：159.75.12.57 来自于：中国 北京  联通"));
    }

    @Test
    void cloudflareTraceUsesTheIpKey() {
        String body = "fl=1164f61\nh=www.cloudflare.com\nip=111.230.93.5\nts=1792249757.000\n"
                + "visit_scheme=https\ncolo=LAX\n";
        assertEquals("111.230.93.5", PublicIpProbe.parseTrace(body));
        assertEquals("111.230.93.5", PublicIpProbe.parse(PublicIpProbe.Source.CLOUDFLARE, body));
        // trace 里别的字段带数字也不能被当成地址
        assertEquals("", PublicIpProbe.parseTrace("fl=1.2.3.4\nh=x\n"));
    }

    @Test
    void invalidBodiesYieldEmpty() {
        assertEquals("", PublicIpProbe.sanitize(""));
        assertEquals("", PublicIpProbe.sanitize(null));
        assertEquals("", PublicIpProbe.sanitize("rate limited"));
        assertEquals("", PublicIpProbe.sanitize("256.1.1.1"));
        assertEquals("", PublicIpProbe.sanitize("1.2.3.4.5"));
        // MAC 地址不能被当成 IP
        assertEquals("", PublicIpProbe.sanitize("设备地址 00:1a:2b:3c:4d:5e 无 IP"));
        // 探测源被劫持时返回这些地址没有意义
        assertEquals("", PublicIpProbe.sanitize("127.0.0.1"));
        assertEquals("", PublicIpProbe.sanitize("0.0.0.0"));
    }

    @Test
    void ipv6IsNotMistakenForIpv4() {
        assertEquals("", PublicIpProbe.parse(PublicIpProbe.Source.IP_SB, "2001:db8::1"));
    }

    @Test
    void probeMergesEverySourceAndKeepsErrorsOutOfBand() {
        PublicIpProbe.Result result = PublicIpProbe.probe(source -> {
            switch (source) {
                case CLOUDFLARE:
                    return "fl=1\nip=198.51.100.7\n";
                case IPIFY:
                    throw new IllegalStateException("Connection refused");
                case IP_SB:
                    return "203.0.113.9\n";
                default:
                    // 与 ip.sb 报同一个地址，合并后只留一份
                    return "当前 IP：203.0.113.9 来自于：中国 北京 联通";
            }
        });

        assertEquals(List.of("198.51.100.7", "203.0.113.9"), result.getAddresses());
        assertEquals("198.51.100.7", result.getSources().get("cloudflare"));
        assertEquals("203.0.113.9", result.getSources().get("ipip"));
        assertEquals("Connection refused", result.getErrors().get("ipify"));
        assertEquals(4, result.getSources().size() + result.getErrors().size());
        assertFalse(result.isEmpty());
    }

    @Test
    void probeSurvivesEverySourceFailing() {
        PublicIpProbe.Result result = PublicIpProbe.probe(source -> {
            throw new java.net.SocketTimeoutException("connect timed out");
        });

        assertTrue(result.isEmpty());
        assertEquals(PublicIpProbe.Source.values().length, result.getErrors().size());
        assertTrue(result.getErrors().values().stream().allMatch(message -> message.contains("timed out")));
    }

    @Test
    void sourcesCoverTheRequiredEndpoints() {
        assertEquals("https://www.cloudflare.com/cdn-cgi/trace", PublicIpProbe.Source.CLOUDFLARE.getUrl());
        assertEquals("https://api.ipify.org/", PublicIpProbe.Source.IPIFY.getUrl());
        assertEquals("https://api.ip.sb/ip", PublicIpProbe.Source.IP_SB.getUrl());
        assertEquals("https://myip.ipip.net/", PublicIpProbe.Source.IPIP.getUrl());
        assertEquals(4, PublicIpProbe.Source.values().length);
    }
}
