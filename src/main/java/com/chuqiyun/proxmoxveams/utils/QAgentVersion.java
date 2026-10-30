package com.chuqiyun.proxmoxveams.utils;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * @Author: 星禾
 * @Description: 读取构建时从被控发布文件打包的版本，避免主控与被控重复维护版本号
 * @DateTime: 2026/10/30 13:41
 */
public final class QAgentVersion {
    private static final String VERSION = readVersion();

    private QAgentVersion() {
    }

    public static String getVersion() {
        return VERSION;
    }

    private static String readVersion() {
        try (InputStream stream = new ClassPathResource("qagent-version").getInputStream()) {
            String version = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (version.isEmpty()) {
                throw new IllegalStateException("主控包内的 QAgent 发布版本为空");
            }
            return version;
        } catch (IOException e) {
            throw new IllegalStateException("无法读取主控包内的 QAgent 发布版本", e);
        }
    }
}
