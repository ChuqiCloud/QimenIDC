package com.chuqiyun.proxmoxveams.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @Author: 星禾
 * @Description: VNC 代理出口来源的 IP/CIDR 校验与规范化，禁止通过地址字段触发 DNS 查询
 * @DateTime: 2026/11/4 10:22
 */
public final class VncSourceNetworks {
    private VncSourceNetworks() {
    }

    public static List<String> parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> networks = new LinkedHashSet<>();
        for (String item : value.trim().split("[,\\s]+")) {
            if (!item.isEmpty()) {
                networks.add(normalize(item));
            }
        }
        if (networks.isEmpty()) {
            throw new IllegalArgumentException("VNC 出口来源必须包含有效的 IP/CIDR");
        }
        List<String> sorted = new ArrayList<>(networks);
        Collections.sort(sorted);
        return sorted;
    }

    public static String normalize(String value) {
        String[] parts = value == null ? new String[0] : value.trim().split("/", -1);
        if (parts.length < 1 || parts.length > 2) {
            throw invalid();
        }
        String literal = parts[0];
        boolean ipv4 = IpUtil.isValidIpv4(literal);
        boolean ipv6 = literal.contains(":") && literal.matches("[0-9a-fA-F:.]+");
        if (!ipv4 && !ipv6) {
            throw invalid();
        }
        try {
            // 上方已限制为数字地址；不能让域名、URL、端口或 scope ID 绕过校验。
            InetAddress address = InetAddress.getByName(literal);
            byte[] bytes = address.getAddress();
            if ((ipv6 && bytes.length != 16) || address.isMulticastAddress()) {
                throw invalid();
            }
            int maxPrefix = bytes.length * 8;
            int prefix = maxPrefix;
            if (parts.length == 2) {
                if (!parts[1].matches("[0-9]{1,3}")) {
                    throw invalid();
                }
                prefix = Integer.parseInt(parts[1]);
            }
            if (prefix <= 0 || prefix > maxPrefix) {
                throw invalid();
            }
            if (address.isAnyLocalAddress() && prefix == maxPrefix) {
                throw invalid();
            }
            for (int i = 0; i < bytes.length; i++) {
                int keep = Math.max(0, Math.min(8, prefix - i * 8));
                bytes[i] = (byte) (bytes[i] & (0xff << (8 - keep)));
            }
            return InetAddress.getByAddress(bytes).getHostAddress() + "/" + prefix;
        } catch (UnknownHostException e) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("VNC 出口来源仅支持有效的 IPv4/IPv6 或 CIDR，不能使用域名、通配地址或 /0 网段");
    }
}
