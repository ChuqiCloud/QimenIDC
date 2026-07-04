package com.chuqiyun.proxmoxveams.utils;

import com.chuqiyun.proxmoxveams.entity.Master;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author: 星禾
 * @Description: cloud-init单网卡多IP配置工具
 * @DateTime: 2026/6/6 12:40
 */
public class CloudInitNetworkUtil {
    private static final String SNIPPET_STORAGE = "local";
    private static final String SNIPPET_DIR = "/var/lib/vz/snippets";
    private static final String INTERFACE_NAME = "eth0";
    private static final Pattern MAC_PATTERN = Pattern.compile("(?i)(?:^|,)(?:virtio|e1000|rtl8139|vmxnet3)=([0-9a-f]{2}(?::[0-9a-f]{2}){5})");
    private static final String NET_MODEL = "virtio";

    public static String getNetworkSnippetFileName(Integer vmid) {
        return "qimen-vm-" + vmid + "-network.yaml";
    }

    public static String getNetworkSnippetPath(Integer vmid) {
        return SNIPPET_DIR + "/" + getNetworkSnippetFileName(vmid);
    }

    public static String getNetworkSnippetVolume(Integer vmid) {
        return SNIPPET_STORAGE + ":snippets/" + getNetworkSnippetFileName(vmid);
    }

    public static String buildStableMacAddress(Integer nodeId, Integer vmid) {
        UUID uuid = UUID.nameUUIDFromBytes(("qimen-" + nodeId + "-" + vmid).getBytes(StandardCharsets.UTF_8));
        long value = uuid.getLeastSignificantBits();
        return String.format(Locale.US, "02:%02x:%02x:%02x:%02x:%02x",
                (value >> 32) & 0xff,
                (value >> 24) & 0xff,
                (value >> 16) & 0xff,
                (value >> 8) & 0xff,
                value & 0xff);
    }

    public static String extractMacAddress(String netConfig) {
        if (StringUtils.isBlank(netConfig)) {
            return null;
        }
        Matcher matcher = MAC_PATTERN.matcher(netConfig);
        if (matcher.find()) {
            return matcher.group(1).toLowerCase();
        }
        return null;
    }

    public static String buildPveNet0Config(String bridge, String macAddress, String rate) {
        return buildPveNet0Config(bridge, macAddress, rate, true);
    }

    public static String buildPveNet0Config(String bridge, String macAddress, String rate, boolean firewallEnabled) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.isNotBlank(macAddress)) {
            parts.add(NET_MODEL + "=" + macAddress.toLowerCase());
        } else {
            parts.add(NET_MODEL);
        }
        if (StringUtils.isNotBlank(bridge)) {
            parts.add("bridge=" + bridge);
        }
        if (StringUtils.isNotBlank(rate)) {
            parts.add("rate=" + rate);
        }
        if (firewallEnabled) {
            parts.add("firewall=1");
        }
        return String.join(",", parts);
    }

    public static String ensurePveNet0Config(String netConfig, String bridge, String macAddress, String rate,
                                             boolean firewallEnabled) {
        if (StringUtils.isBlank(netConfig)) {
            return buildPveNet0Config(bridge, macAddress, rate, firewallEnabled);
        }

        String[] tokens = netConfig.split(",");
        List<String> extras = new ArrayList<>();
        LinkedHashMap<String, String> optionMap = new LinkedHashMap<>();
        String modelToken = tokens[0].trim();
        String existingMac = extractMacAddress(modelToken);
        boolean hasModelMac = StringUtils.isNotBlank(existingMac);

        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i].trim();
            if (StringUtils.isBlank(token)) {
                continue;
            }
            int idx = token.indexOf('=');
            if (idx > 0) {
                optionMap.put(token.substring(0, idx), token.substring(idx + 1));
            } else {
                extras.add(token);
            }
        }

        if (StringUtils.isNotBlank(bridge)) {
            optionMap.put("bridge", bridge);
        }
        if (StringUtils.isNotBlank(rate)) {
            optionMap.put("rate", rate);
        }
        if (firewallEnabled) {
            optionMap.put("firewall", "1");
        }

        if (StringUtils.isNotBlank(macAddress)) {
            String normalizedMac = macAddress.toLowerCase();
            if (hasModelMac) {
                modelToken = modelToken.substring(0, modelToken.indexOf('=') + 1) + normalizedMac;
            } else if (modelToken.contains("=")) {
                String modelName = modelToken.substring(0, modelToken.indexOf('='));
                modelToken = modelName + "=" + normalizedMac;
            } else {
                modelToken = NET_MODEL + "=" + normalizedMac;
            }
        }

        List<String> parts = new ArrayList<>();
        parts.add(modelToken);
        for (Map.Entry<String, String> entry : optionMap.entrySet()) {
            parts.add(entry.getKey() + "=" + entry.getValue());
        }
        parts.addAll(extras);
        return String.join(",", parts);
    }

    public static String removePveNet0FirewallConfig(String netConfig) {
        if (StringUtils.isBlank(netConfig)) {
            return netConfig;
        }
        List<String> parts = new ArrayList<>();
        for (String token : netConfig.split(",")) {
            String item = token.trim();
            if (StringUtils.isBlank(item)) {
                continue;
            }
            int idx = item.indexOf('=');
            String key = idx > 0 ? item.substring(0, idx).trim() : item;
            if ("firewall".equalsIgnoreCase(key)) {
                continue;
            }
            parts.add(item);
        }
        return String.join(",", parts);
    }

    public static int getIpAddressCount(Map<String, String> ipConfig) {
        return parseIpConfigs(ipConfig).size();
    }

    public static String getPrimaryIpConfig(Map<String, String> ipConfig) {
        List<Map.Entry<String, String>> entries = getSortedIpConfigEntries(ipConfig);
        for (Map.Entry<String, String> entry : entries) {
            if (parseIpConfig(entry.getValue()) != null) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static List<String> getIpList(Map<String, String> ipConfig) {
        List<String> ipList = new ArrayList<>();
        for (CloudInitIpConfig item : parseIpConfigs(ipConfig)) {
            ipList.add(item.ip);
        }
        return ipList;
    }

    public static List<String> getIpv4List(Map<String, String> ipConfig) {
        List<String> ipList = new ArrayList<>();
        for (CloudInitIpConfig item : parseIpConfigs(ipConfig)) {
            if (!item.ipv6) {
                ipList.add(item.ip);
            }
        }
        return ipList;
    }

    public static List<String> getIpv6List(Map<String, String> ipConfig) {
        List<String> ipList = new ArrayList<>();
        for (CloudInitIpConfig item : parseIpConfigs(ipConfig)) {
            if (item.ipv6) {
                ipList.add(item.ip);
            }
        }
        return ipList;
    }

    public static Map<String, String> getIpAddressMap(Map<String, String> ipConfig) {
        Map<String, String> ipAddressMap = new LinkedHashMap<>();
        for (CloudInitIpConfig item : parseIpConfigs(ipConfig)) {
            ipAddressMap.put(item.ip, item.address);
        }
        return ipAddressMap;
    }

    public static String getIpFromCloudInitConfig(String ipConfig) {
        CloudInitIpConfig config = parseIpConfig(ipConfig);
        return config == null ? null : config.ip;
    }

    public static String getAddressFromCloudInitConfig(String ipConfig) {
        CloudInitIpConfig config = parseIpConfig(ipConfig);
        return config == null ? null : config.address;
    }

    public static Integer getPrefixLength(String address) {
        if (StringUtils.isBlank(address)) {
            return null;
        }
        int maskIndex = address.indexOf('/');
        if (maskIndex < 0 || maskIndex == address.length() - 1) {
            return null;
        }
        try {
            return Integer.parseInt(address.substring(maskIndex + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String buildSingleNicNetworkConfig(Map<String, String> ipConfig, List<String> nameservers) {
        return buildSingleNicNetworkConfig(ipConfig, nameservers, null);
    }

    public static String buildSingleNicNetworkConfig(Map<String, String> ipConfig, List<String> nameservers,
                                                     String macAddress) {
        List<CloudInitIpConfig> ipConfigs = parseIpConfigs(ipConfig);
        if (ipConfigs.isEmpty()) {
            throw new IllegalArgumentException("cloud-init网络配置不能为空");
        }
        String gateway4 = getPrimaryGateway(ipConfigs, false);
        String gateway6 = getPrimaryGateway(ipConfigs, true);
        StringBuilder builder = new StringBuilder();
        builder.append("version: 2\n");
        builder.append("ethernets:\n");
        builder.append("  ").append(INTERFACE_NAME).append(":\n");
        if (StringUtils.isNotBlank(macAddress)) {
            builder.append("    match:\n");
            builder.append("      macaddress: \"").append(macAddress.toLowerCase()).append("\"\n");
            builder.append("    set-name: ").append(INTERFACE_NAME).append("\n");
        }
        builder.append("    dhcp4: false\n");
        builder.append("    dhcp6: false\n");
        builder.append("    addresses:\n");
        for (CloudInitIpConfig item : ipConfigs) {
            builder.append("      - ").append(item.address).append("\n");
        }
        if (StringUtils.isNotBlank(gateway4)) {
            builder.append("    gateway4: ").append(gateway4).append("\n");
        }
        if (StringUtils.isNotBlank(gateway6)) {
            builder.append("    gateway6: ").append(gateway6).append("\n");
        }
        if (nameservers != null && !nameservers.isEmpty()) {
            builder.append("    nameservers:\n");
            builder.append("      addresses:\n");
            for (String nameserver : nameservers) {
                if (StringUtils.isNotBlank(nameserver)) {
                    builder.append("        - ").append(nameserver.trim()).append("\n");
                }
            }
        }
        return builder.toString();
    }

    public static void uploadSingleNicNetworkSnippet(Master node, Integer vmid, Map<String, String> ipConfig,
                                                     List<String> nameservers) throws JSchException, SftpException {
        uploadSingleNicNetworkSnippet(node, vmid, ipConfig, nameservers, null);
    }

    public static void uploadSingleNicNetworkSnippet(Master node, Integer vmid, Map<String, String> ipConfig,
                                                     List<String> nameservers, String macAddress) throws JSchException, SftpException {
        if (node == null || StringUtils.isBlank(node.getHost()) || node.getSshPort() == null
                || StringUtils.isBlank(node.getSshUsername()) || StringUtils.isBlank(node.getSshPassword())) {
            throw new IllegalStateException("节点SSH配置不完整，无法写入cloud-init网络配置");
        }
        SshUtil sshUtil = new SshUtil(node.getHost(), node.getSshPort(), node.getSshUsername(), node.getSshPassword());
        try {
            sshUtil.connect();
            sshUtil.uploadTextFile(getNetworkSnippetPath(vmid), buildSingleNicNetworkConfig(ipConfig, nameservers, macAddress));
        } finally {
            sshUtil.disconnect();
        }
    }

    private static String getPrimaryGateway(List<CloudInitIpConfig> ipConfigs, boolean ipv6) {
        for (CloudInitIpConfig item : ipConfigs) {
            if (item.ipv6 == ipv6 && StringUtils.isNotBlank(item.gateway)) {
                return item.gateway;
            }
        }
        return null;
    }

    private static List<CloudInitIpConfig> parseIpConfigs(Map<String, String> ipConfig) {
        List<CloudInitIpConfig> ipConfigs = new ArrayList<>();
        for (Map.Entry<String, String> entry : getSortedIpConfigEntries(ipConfig)) {
            ipConfigs.addAll(parseIpConfigList(entry.getValue()));
        }
        return ipConfigs;
    }

    private static List<Map.Entry<String, String>> getSortedIpConfigEntries(Map<String, String> ipConfig) {
        List<Map.Entry<String, String>> entries = new ArrayList<>();
        if (ipConfig == null || ipConfig.isEmpty()) {
            return entries;
        }
        entries.addAll(ipConfig.entrySet());
        entries.sort(Comparator.comparingInt(entry -> getIpConfigIndex(entry.getKey())));
        return entries;
    }

    private static int getIpConfigIndex(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static CloudInitIpConfig parseIpConfig(String ipConfig) {
        List<CloudInitIpConfig> ipConfigs = parseIpConfigList(ipConfig);
        return ipConfigs.isEmpty() ? null : ipConfigs.get(0);
    }

    private static List<CloudInitIpConfig> parseIpConfigList(String ipConfig) {
        List<CloudInitIpConfig> ipConfigs = new ArrayList<>();
        if (StringUtils.isBlank(ipConfig)) {
            return ipConfigs;
        }
        String address4 = null;
        String gateway4 = null;
        String address6 = null;
        String gateway6 = null;
        String[] configItems = ipConfig.split(",");
        for (String configItem : configItems) {
            String item = configItem.trim();
            if (item.startsWith("ip=")) {
                address4 = item.substring(3);
            } else if (item.startsWith("ip6=")) {
                address6 = item.substring(4);
            } else if (item.startsWith("gw=")) {
                gateway4 = item.substring(3);
            } else if (item.startsWith("gw6=")) {
                gateway6 = item.substring(4);
            }
        }
        CloudInitIpConfig ipv4Config = buildCloudInitIpConfig(address4, gateway4, false);
        if (ipv4Config != null) {
            ipConfigs.add(ipv4Config);
        }
        CloudInitIpConfig ipv6Config = buildCloudInitIpConfig(address6, gateway6, true);
        if (ipv6Config != null) {
            ipConfigs.add(ipv6Config);
        }
        return ipConfigs;
    }

    private static CloudInitIpConfig buildCloudInitIpConfig(String address, String gateway, boolean ipv6) {
        if (StringUtils.isBlank(address) || "dhcp".equalsIgnoreCase(address)) {
            return null;
        }
        String ip = address;
        int maskIndex = address.indexOf('/');
        if (maskIndex > 0) {
            ip = address.substring(0, maskIndex);
        }
        return new CloudInitIpConfig(ip, address, gateway, ipv6);
    }

    public static List<String> distinctNameservers(List<String> nameservers) {
        Set<String> nameserverSet = new LinkedHashSet<>();
        if (nameservers != null) {
            for (String nameserver : nameservers) {
                if (StringUtils.isNotBlank(nameserver)) {
                    nameserverSet.add(nameserver.trim());
                }
            }
        }
        return new ArrayList<>(nameserverSet);
    }

    private static class CloudInitIpConfig {
        private final String ip;
        private final String address;
        private final String gateway;
        private final boolean ipv6;

        private CloudInitIpConfig(String ip, String address, String gateway, boolean ipv6) {
            this.ip = ip;
            this.address = address;
            this.gateway = gateway;
            this.ipv6 = ipv6;
        }
    }
}
