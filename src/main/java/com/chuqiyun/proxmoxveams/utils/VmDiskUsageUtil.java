package com.chuqiyun.proxmoxveams.utils;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VmDiskUsageUtil {
    private static final Pattern DISK_DEVICE_PATTERN = Pattern.compile("^(scsi|virtio|sata|ide)(\\d+)$");
    private static final Pattern SIZE_PATTERN = Pattern.compile("(?:^|,)size=([0-9]+(?:\\.[0-9]+)?)([KMGTPE]?)", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Integer> BUS_ORDER = buildBusOrder();

    private VmDiskUsageUtil() {
    }

    public static JSONObject buildRequest(JSONObject vmConfig) {
        JSONObject request = new JSONObject();
        JSONArray disks = new JSONArray();
        if (vmConfig == null) {
            request.put("system_device", null);
            request.put("disks", disks);
            return request;
        }

        List<DiskConfig> diskConfigs = new ArrayList<>();
        for (String key : vmConfig.keySet()) {
            Matcher deviceMatcher = DISK_DEVICE_PATTERN.matcher(key);
            if (!deviceMatcher.matches()) {
                continue;
            }
            DiskConfig diskConfig = parseDiskConfig(key, vmConfig.getString(key));
            if (diskConfig != null) {
                diskConfigs.add(diskConfig);
            }
        }
        diskConfigs.sort(Comparator
                .comparingInt((DiskConfig disk) -> BUS_ORDER.getOrDefault(disk.bus, Integer.MAX_VALUE))
                .thenComparingInt(disk -> disk.index));

        String systemDevice = findSystemDevice(vmConfig.getString("boot"), diskConfigs);
        for (DiskConfig diskConfig : diskConfigs) {
            JSONObject disk = new JSONObject();
            disk.put("device", diskConfig.device);
            disk.put("volume", diskConfig.volume);
            disk.put("provisioned_bytes", diskConfig.provisionedBytes);
            disks.add(disk);
        }
        request.put("system_device", systemDevice);
        request.put("disks", disks);
        return request;
    }

    private static DiskConfig parseDiskConfig(String device, String config) {
        if (config == null) {
            return null;
        }
        String normalized = config.trim();
        String lowerConfig = normalized.toLowerCase();
        if (normalized.isEmpty() || lowerConfig.contains("media=cdrom")) {
            return null;
        }

        String volume = normalized.split(",", 2)[0].trim();
        if (volume.isEmpty() || "none".equalsIgnoreCase(volume) || volume.toLowerCase().endsWith(":cloudinit")) {
            return null;
        }

        Matcher deviceMatcher = DISK_DEVICE_PATTERN.matcher(device);
        if (!deviceMatcher.matches()) {
            return null;
        }
        return new DiskConfig(
                device,
                deviceMatcher.group(1),
                Integer.parseInt(deviceMatcher.group(2)),
                volume,
                parseSizeBytes(normalized)
        );
    }

    private static String findSystemDevice(String bootConfig, List<DiskConfig> disks) {
        if (bootConfig != null) {
            String order = bootConfig.startsWith("order=") ? bootConfig.substring("order=".length()) : bootConfig;
            for (String bootDevice : order.split(";")) {
                String normalizedDevice = bootDevice.trim();
                if (disks.stream().anyMatch(disk -> disk.device.equals(normalizedDevice))) {
                    return normalizedDevice;
                }
            }
        }
        if (disks.stream().anyMatch(disk -> "scsi0".equals(disk.device))) {
            return "scsi0";
        }
        return disks.isEmpty() ? null : disks.get(0).device;
    }

    public static Long getDiskSizeBytes(String config) {
        return parseSizeBytes(config);
    }

    private static Long parseSizeBytes(String config) {
        if (config == null || config.trim().isEmpty()) {
            return null;
        }
        Matcher matcher = SIZE_PATTERN.matcher(config);
        if (!matcher.find()) {
            return null;
        }
        BigDecimal size = new BigDecimal(matcher.group(1));
        int exponent = unitExponent(matcher.group(2));
        return size.multiply(BigDecimal.valueOf(1024).pow(exponent)).longValue();
    }

    private static int unitExponent(String unit) {
        switch (unit.toUpperCase()) {
            case "K":
                return 1;
            case "M":
                return 2;
            case "G":
                return 3;
            case "T":
                return 4;
            case "P":
                return 5;
            case "E":
                return 6;
            default:
                return 0;
        }
    }

    private static Map<String, Integer> buildBusOrder() {
        Map<String, Integer> order = new HashMap<>();
        order.put("scsi", 0);
        order.put("virtio", 1);
        order.put("sata", 2);
        order.put("ide", 3);
        return order;
    }

    private static final class DiskConfig {
        private final String device;
        private final String bus;
        private final int index;
        private final String volume;
        private final Long provisionedBytes;

        private DiskConfig(String device, String bus, int index, String volume, Long provisionedBytes) {
            this.device = device;
            this.bus = bus;
            this.index = index;
            this.volume = volume;
            this.provisionedBytes = provisionedBytes;
        }
    }
}
