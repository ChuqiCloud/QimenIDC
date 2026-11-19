package com.chuqiyun.proxmoxveams.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 固定的受信救援镜像目录，镜像只允许从统一镜像站下载。 */
public final class RescueImageCatalog {
    public static final String WINDOWS_ID = "windows-wepe";
    public static final String LINUX_ID = "linux-systemrescue";

    private static final Map<String, Image> IMAGES;

    static {
        Map<String, Image> images = new LinkedHashMap<>();
        images.put(WINDOWS_ID, new Image(WINDOWS_ID, "Windows PE", "windows",
                "WePE_64_V2.3.iso", "https://mirror.chuqiyun.com/iso/WePE_64_V2.3.iso"));
        images.put(LINUX_ID, new Image(LINUX_ID, "SystemRescue 13.03", "linux",
                "systemrescue-13.03-amd64.iso",
                "https://mirror.chuqiyun.com/iso/systemrescue-13.03-amd64.iso"));
        IMAGES = Collections.unmodifiableMap(images);
    }

    private RescueImageCatalog() {
    }

    public static Image get(String imageId) {
        return IMAGES.get(imageId);
    }

    public static List<Map<String, Object>> asMaps() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Image image : IMAGES.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", image.id());
            item.put("name", image.name());
            item.put("type", image.type());
            item.put("fileName", image.fileName());
            item.put("url", image.url());
            result.add(item);
        }
        return result;
    }

    public record Image(String id, String name, String type, String fileName, String url) {
    }
}
