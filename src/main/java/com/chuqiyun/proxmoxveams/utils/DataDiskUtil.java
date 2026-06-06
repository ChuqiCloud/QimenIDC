package com.chuqiyun.proxmoxveams.utils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author: 星禾
 * @Description: 数据盘参数处理工具
 * @DateTime: 2026/6/6 12:40
 */
public class DataDiskUtil {
    private static final Pattern SCSI_KEY_PATTERN = Pattern.compile("(?i)^scsi(\\d+)$");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^-?\\d+(?:\\.\\d+)?$");
    private static final Set<String> OPTION_IGNORE_KEYS = new LinkedHashSet<>();

    static {
        OPTION_IGNORE_KEYS.add("size");
        OPTION_IGNORE_KEYS.add("diskSize");
        OPTION_IGNORE_KEYS.add("dataDiskSize");
        OPTION_IGNORE_KEYS.add("capacity");
        OPTION_IGNORE_KEYS.add("storage");
        OPTION_IGNORE_KEYS.add("pool");
        OPTION_IGNORE_KEYS.add("storageName");
        OPTION_IGNORE_KEYS.add("slot");
        OPTION_IGNORE_KEYS.add("index");
        OPTION_IGNORE_KEYS.add("name");
        OPTION_IGNORE_KEYS.add("value");
        OPTION_IGNORE_KEYS.add("disk");
        OPTION_IGNORE_KEYS.add("diskValue");
        OPTION_IGNORE_KEYS.add("config");
        OPTION_IGNORE_KEYS.add("enable");
        OPTION_IGNORE_KEYS.add("enabled");
    }

    public static HashMap<String, Object> buildCreateDataDiskParams(String defaultStorage, Map<Object, Object> dataDiskMap) {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        List<DataDiskItem> dataDiskItems = getSortedDataDiskItems(dataDiskMap);
        Set<Integer> usedSlotSet = new LinkedHashSet<>();
        int nextSlot = 1;
        for (DataDiskItem item : dataDiskItems) {
            String diskValue = buildDiskValue(defaultStorage, item.value);
            if (isBlank(diskValue)) {
                continue;
            }
            int slot = item.slot == null || item.slot < 1 ? nextSlot : item.slot;
            while (slot < 1 || usedSlotSet.contains(slot)) {
                slot = nextSlot;
                nextSlot++;
            }
            params.put("scsi" + slot, diskValue);
            usedSlotSet.add(slot);
            nextSlot = Math.max(nextSlot, slot + 1);
        }
        return params;
    }

    public static List<String> getDataDiskNames(Map<Object, Object> dataDiskMap) {
        List<String> diskNames = new ArrayList<>();
        Set<Integer> usedSlotSet = new LinkedHashSet<>();
        int nextSlot = 1;
        for (DataDiskItem item : getSortedDataDiskItems(dataDiskMap)) {
            String diskValue = buildDiskValue("local", item.value);
            if (isBlank(diskValue)) {
                continue;
            }
            int slot = item.slot == null || item.slot < 1 ? nextSlot : item.slot;
            while (slot < 1 || usedSlotSet.contains(slot)) {
                slot = nextSlot;
                nextSlot++;
            }
            diskNames.add("scsi" + slot);
            usedSlotSet.add(slot);
            nextSlot = Math.max(nextSlot, slot + 1);
        }
        return diskNames;
    }

    private static List<DataDiskItem> getSortedDataDiskItems(Map<Object, Object> dataDiskMap) {
        List<DataDiskItem> items = new ArrayList<>();
        if (dataDiskMap == null || dataDiskMap.isEmpty()) {
            return items;
        }
        int order = 0;
        for (Map.Entry<Object, Object> entry : dataDiskMap.entrySet()) {
            Integer slot = getSlot(entry.getKey(), entry.getValue());
            items.add(new DataDiskItem(slot, entry.getValue(), order));
            order++;
        }
        items.sort(Comparator
                .comparing((DataDiskItem item) -> item.slot == null ? Integer.MAX_VALUE : item.slot)
                .thenComparingInt(item -> item.order));
        return items;
    }

    private static Integer getSlot(Object key, Object value) {
        Integer slot = getSlotFromValue(key);
        if (slot != null) {
            return slot;
        }
        if (value instanceof Map<?, ?> valueMap) {
            slot = getSlotFromValue(valueMap.get("slot"));
            if (slot != null) {
                return slot;
            }
            return getSlotFromValue(valueMap.get("index"));
        }
        return null;
    }

    private static Integer getSlotFromValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        Matcher matcher = SCSI_KEY_PATTERN.matcher(text);
        if (matcher.matches()) {
            return parseInteger(matcher.group(1));
        }
        return parseInteger(text);
    }

    private static String buildDiskValue(String defaultStorage, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> valueMap) {
            return buildDiskValueFromMap(defaultStorage, valueMap);
        }
        return buildDiskValueFromRaw(defaultStorage, value);
    }

    private static String buildDiskValueFromMap(String defaultStorage, Map<?, ?> valueMap) {
        if (isDisabled(valueMap.get("enable")) || isDisabled(valueMap.get("enabled"))) {
            return null;
        }
        String rawValue = firstText(valueMap, "value", "disk", "diskValue", "config");
        if (!isBlank(rawValue)) {
            return buildDiskValueFromRaw(firstTextOrDefault(valueMap, defaultStorage, "storage", "pool", "storageName"), rawValue);
        }
        String size = firstText(valueMap, "size", "diskSize", "dataDiskSize", "capacity");
        size = normalizeSize(size);
        if (isBlank(size)) {
            return null;
        }
        String storage = firstTextOrDefault(valueMap, defaultStorage, "storage", "pool", "storageName");
        String diskValue = appendStorage(storage, size);
        String options = buildDiskOptions(valueMap);
        return isBlank(options) ? diskValue : diskValue + options;
    }

    private static String buildDiskValueFromRaw(String defaultStorage, Object value) {
        String diskValue = value.toString().trim();
        if (isBlank(diskValue)) {
            return null;
        }
        if (diskValue.contains(":")) {
            return diskValue;
        }
        diskValue = normalizeSize(diskValue);
        if (isBlank(diskValue)) {
            return null;
        }
        return appendStorage(defaultStorage, diskValue);
    }

    private static String appendStorage(String storage, String diskValue) {
        if (isBlank(storage)) {
            throw new IllegalArgumentException("数据盘存储不能为空");
        }
        return storage.trim() + ":" + diskValue;
    }

    private static String buildDiskOptions(Map<?, ?> valueMap) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<?, ?> entry : valueMap.entrySet()) {
            String key = entry.getKey() == null ? null : entry.getKey().toString();
            if (isBlank(key) || OPTION_IGNORE_KEYS.contains(key)) {
                continue;
            }
            Object value = entry.getValue();
            if (value == null || value instanceof Map<?, ?> || value instanceof Iterable<?>) {
                continue;
            }
            String text = value instanceof Boolean ? ((Boolean) value ? "1" : "0") : value.toString().trim();
            if (!isBlank(text)) {
                builder.append(",").append(key).append("=").append(text);
            }
        }
        return builder.toString();
    }

    private static String normalizeSize(String value) {
        if (isBlank(value)) {
            return null;
        }
        String text = value.trim();
        if (NUMBER_PATTERN.matcher(text).matches()) {
            BigDecimal number = new BigDecimal(text);
            if (number.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return text;
        }
        return text;
    }

    private static String firstText(Map<?, ?> valueMap, String... keys) {
        for (String key : keys) {
            Object value = valueMap.get(key);
            if (value != null && !isBlank(value.toString())) {
                return value.toString().trim();
            }
        }
        return null;
    }

    private static String firstTextOrDefault(Map<?, ?> valueMap, String defaultValue, String... keys) {
        String value = firstText(valueMap, keys);
        return isBlank(value) ? defaultValue : value;
    }

    private static boolean isDisabled(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return !bool;
        }
        String text = value.toString().trim();
        return "0".equals(text) || "false".equalsIgnoreCase(text) || "no".equalsIgnoreCase(text);
    }

    private static Integer parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class DataDiskItem {
        private final Integer slot;
        private final Object value;
        private final int order;

        private DataDiskItem(Integer slot, Object value, int order) {
            this.slot = slot;
            this.value = value;
            this.order = order;
        }
    }
}
