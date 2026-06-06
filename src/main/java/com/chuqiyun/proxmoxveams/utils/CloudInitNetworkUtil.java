package com.chuqiyun.proxmoxveams.utils;

import com.chuqiyun.proxmoxveams.entity.Master;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
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

    public static int getIpAddressCount(Map<String, String> ipConfig) {
        return parseIpConfigs(ipConfig).size();
    }

    public static String getPrimaryIpConfig(Map<String, String> ipConfig) {
        List<Map.Entry<String, String>> entries = getSortedIpConfigEntries(ipConfig);
        return entries.isEmpty() ? null : entries.get(0).getValue();
    }

    public static List<String> getIpList(Map<String, String> ipConfig) {
        List<String> ipList = new ArrayList<>();
        for (CloudInitIpConfig item : parseIpConfigs(ipConfig)) {
            ipList.add(item.ip);
        }
        return ipList;
    }

    public static String getIpFromCloudInitConfig(String ipConfig) {
        CloudInitIpConfig config = parseIpConfig(ipConfig);
        return config == null ? null : config.ip;
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
        String gateway = getPrimaryGateway(ipConfigs);
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
        builder.append("    addresses:\n");
        for (CloudInitIpConfig item : ipConfigs) {
            builder.append("      - ").append(item.address).append("\n");
        }
        if (StringUtils.isNotBlank(gateway)) {
            builder.append("    gateway4: ").append(gateway).append("\n");
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

    private static String getPrimaryGateway(List<CloudInitIpConfig> ipConfigs) {
        for (CloudInitIpConfig item : ipConfigs) {
            if (StringUtils.isNotBlank(item.gateway)) {
                return item.gateway;
            }
        }
        return null;
    }

    private static List<CloudInitIpConfig> parseIpConfigs(Map<String, String> ipConfig) {
        List<CloudInitIpConfig> ipConfigs = new ArrayList<>();
        for (Map.Entry<String, String> entry : getSortedIpConfigEntries(ipConfig)) {
            CloudInitIpConfig item = parseIpConfig(entry.getValue());
            if (item != null) {
                ipConfigs.add(item);
            }
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
        if (StringUtils.isBlank(ipConfig)) {
            return null;
        }
        String address = null;
        String gateway = null;
        String[] configItems = ipConfig.split(",");
        for (String configItem : configItems) {
            String item = configItem.trim();
            if (item.startsWith("ip=")) {
                address = item.substring(3);
            } else if (item.startsWith("gw=")) {
                gateway = item.substring(3);
            }
        }
        if (StringUtils.isBlank(address) || "dhcp".equalsIgnoreCase(address)) {
            return null;
        }
        String ip = address;
        int maskIndex = address.indexOf('/');
        if (maskIndex > 0) {
            ip = address.substring(0, maskIndex);
        }
        return new CloudInitIpConfig(ip, address, gateway);
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

        private CloudInitIpConfig(String ip, String address, String gateway) {
            this.ip = ip;
            this.address = address;
            this.gateway = gateway;
        }
    }
}
