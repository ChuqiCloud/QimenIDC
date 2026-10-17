package com.chuqiyun.proxmoxveams.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 被控端接口参数schema。
 * <p>
 * 与 Controlled/entity 下的请求实体保持一致，主控在调用前过滤未声明字段，
 * 被控端中间件会再做一次同样的过滤，两侧互不依赖对方的实现。
 *
 * @author 星禾
 * @date 2026/10/14
 */
public class ControllerApiSchema {
    private static final Map<String, Set<String>> SCHEMA = new HashMap<>();

    static {
        // file
        put("/pathFile", "path");
        put("/wget", "url", "path");
        put("/wget/status", "url", "path");
        put("/deleteFile", "path", "file");
        put("/readFile", "path", "filename");
        put("/writeFile", "path", "filename", "content");
        // 管理源白名单同步
        put("/net/trust", "networks", "cluster_id");
        // 虚拟机
        put("/changePassword", "id", "username", "password");
        put("/importDisk", "vmid", "image_path", "save_path");
        put("/disk/usage", "system_device", "disks");
        // vnc
        put("/vnc", "vnc_file_path", "vmid", "username", "password", "port", "host", "time");
        put("/vnc/stop", "vnc_file_path", "vmid", "username", "password", "port", "host", "time");
        put("/vnc/import", "vnc_file_path", "vmid", "username", "password", "port", "host", "time");
        put("/vnc/ensure", "vnc_file_path", "vmid", "username", "password", "port", "host", "time");
        // nat
        put("/nat/add", "vm", "source_ip", "source_port", "destination_ip", "destination_port", "protocol");
        put("/nat/delete", "vm", "source_ip", "source_port", "destination_ip", "destination_port", "protocol");
        put("/nat/addIpForward", "vm", "source_ip", "destination_ip");
        put("/nat/deleteIpForward", "vm", "source_ip", "destination_ip");
        put("/nat/addBridge", "nataddr", "bridge");
        put("/nat/sync", "port_rules", "ip_forward_rules");
        put("/nat/export");
        // 安全组
        put("/security-group/apply", "hostId", "vmId", "networkType", "targetIps",
                "defaultIngressAction", "defaultEgressAction", "rules");
        put("/security-group/delete", "hostId");
        put("/security-group/check", "hostId");
        // 迁移（阶段化接口，主控按步骤调用并可单步重试）
        put("/migrationAction/backup", "task_id", "action", "vmid", "backup_dir", "shutdown_timeout");
        put("/migrationAction/run", "task_id", "action", "vmid", "backup_dir", "shutdown_timeout",
                "source_url", "source_token", "target_vmid", "target_storage");
        put("/migrationAction/cleanup-source", "task_id", "vmid", "purge");
    }

    private static void put(String path, String... fields) {
        SCHEMA.put(path, Collections.unmodifiableSet(new HashSet<>(Arrays.asList(fields))));
    }

    /**
     * 获取接口允许的参数名集合，未登记的接口返回空集合（不做过滤）
     */
    public static Set<String> getAllowedFields(String rawUrl) {
        int index = rawUrl.indexOf('?');
        String path = index < 0 ? rawUrl : rawUrl.substring(0, index);
        for (Map.Entry<String, Set<String>> entry : SCHEMA.entrySet()) {
            if (path.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return Collections.emptySet();
    }
}
