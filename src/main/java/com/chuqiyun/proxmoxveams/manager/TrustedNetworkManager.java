package com.chuqiyun.proxmoxveams.manager;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.config.RestTemplateConfig;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import com.chuqiyun.proxmoxveams.utils.PublicIpProbe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 被控端可信管理源白名单同步。
 * <p>
 * 被控端 nginx 只放行写死在 geo 块里的管理源地址，主控/机房出口 IP 一换，
 * 节点就会以「连不上」的方式整体失联。这里把集群内所有主控的出口地址汇总后
 * 下发到每个被控端，由被控端与它自己实测到的对端地址取并集后落盘并重载 nginx。
 * <p>
 * 同步分两段：启动期由启动编排调用 {@link #initialSync()}；周期心跳按间隔重新探测并同步所有节点。
 * 各成员来源存入共享数据库，节点按租约保留来源，避免主备相互覆盖。
 * <p>
 * 主控自报的出口地址优先取自公共 IP 探测源（{@link PublicIpProbe}），多个源并发后合并；
 * 探测全挂时才退化为本地网卡地址，保证有东西可下发。
 *
 * @author 星禾
 * @date 2026/10/17
 */
@Component
public class TrustedNetworkManager {

    /**
     * 周期重试间隔（毫秒）
     */
    public static final long RETRY_INTERVAL_MILLIS = 5 * 60 * 1000L;

    /**
     * 启动期同步只做一轮，单节点超时由被控端请求超时兜底
     */
    private static final int SYNC_TIMEOUT_MILLIS = 15_000;

    @Resource
    private MasterService masterService;

    @Resource
    private ConfigService configService;
    @Resource
    private JdbcTemplate jdbc;
    @Resource
    private PlatformTransactionManager transactionManager;
    @Value("${config.whitelist.member-id:}")
    private String configuredMemberId;
    private volatile String memberId;
    private static final long MEMBER_LEASE_MILLIS = 24 * 60 * 60 * 1000L;

    /**
     * JDBC 连接串，用于派生集群标识；不出现在日志里
     */
    @Value("${spring.datasource.url:}")
    private String datasourceUrl;
    @Value("${config.whitelist.cluster-id:}")
    private String configuredClusterId;

    /**
     * 节点ID -> 上次成功同步时间
     */
    private final Map<Integer, Long> syncedAt = new ConcurrentHashMap<>();

    /**
     * 节点ID -> 下次允许重试时间
     */
    private final Map<Integer, Long> nextRetryAt = new ConcurrentHashMap<>();

    /**
     * 集群内各主控上报的出口地址，键为主控标识
     */
    private final Map<String, Set<String>> memberAddresses = new ConcurrentHashMap<>();

    private volatile String clusterId;

    /**
     * 本机出口地址（CIDR 串），探测全部失败时退化为网卡地址
     */
    private volatile String localAddresses = "";

    /**
     * 各探测源报告的本机出口地址，键为源名
     */
    private final Map<String, String> probedAddresses = new ConcurrentHashMap<>();

    /**
     * 探测失败的源及原因
     */
    private final Map<String, String> probeErrors = new ConcurrentHashMap<>();

    /**
     * 出口探测专用的 HTTP 客户端：公共站点用的是正规证书，这里按系统默认信任链校验
     */
    private static final RestTemplate PROBE_TEMPLATE = RestTemplateConfig.createProbeRestTemplate();

    private static final String PROBE_USER_AGENT =
            "Mozilla/5.0 (compatible; QimenIDC/1.0; +https://qimenidc.com)";

    private volatile long lastFullSyncAt;

    /**
     * 启动期同步：发现集群内所有主控的出口地址，并把它下发给每个节点
     *
     * @return 同步成功的节点数
     */
    public synchronized int initialSync() {
        String token = configService.getToken();
        List<Master> nodes = listAgentNodes();
        if (nodes.isEmpty()) {
            return 0;
        }
        if (token == null || token.isEmpty()) {
            UnifiedLogger.warn(UnifiedLogger.LogType.SYSTEM, "[Whitelist] 主控未配置被控端token，跳过可信白名单同步");
            return 0;
        }
        String cluster = getClusterId();
        refreshLocalAddresses();

        int succeeded = 0;
        for (Master node : nodes) {
            if (syncNode(node, token, cluster)) {
                succeeded++;
            }
        }
        UnifiedLogger.log(UnifiedLogger.LogType.SYSTEM, "[Whitelist] 可信白名单同步完成: 节点={}, 成功={}, 集群={}, 主控出口={}",
                nodes.size(), succeeded, cluster, localAddresses);
        return succeeded;
    }

    /**
     * 周期同步：成功节点到期也刷新，被控端内容无变化时不会重载 nginx。
     */
    public synchronized void retryPending() {
        String token = configService.getToken();
        if (token == null || token.isEmpty()) {
            return;
        }
        String cluster = getClusterId();
        long now = System.currentTimeMillis();
        if (now - lastFullSyncAt >= RETRY_INTERVAL_MILLIS) {
            refreshLocalAddresses();
        }
        for (Master node : listAgentNodes()) {
            long lastSuccess = syncedAt.getOrDefault(node.getId(), 0L);
            if (now - lastSuccess < RETRY_INTERVAL_MILLIS || now < nextRetryAt.getOrDefault(node.getId(), 0L)) {
                continue;
            }
            syncNode(node, token, cluster);
        }
    }

    private void refreshLocalAddresses() {
        String detected = detectLocalAddresses();
        if (!detected.isEmpty()) {
            localAddresses = detected;
        }
        Set<String> addresses = splitAddresses(localAddresses);
        memberAddresses.put(getMemberKey(), addresses);
        storeAddresses("local", 0, addresses);
        lastFullSyncAt = System.currentTimeMillis();
    }

    private void storeAddresses(String source, int nodeId, Set<String> addresses) {
        if (addresses.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("delete from agent_trust where cluster_id = ? and member_id = ? and source = ? and node_id = ?",
                    getClusterId(), getMemberKey(), source, nodeId);
            for (String address : addresses) {
                jdbc.update("insert into agent_trust (cluster_id, member_id, address, source, node_id, create_time, last_seen) "
                                + "values (?, ?, ?, ?, ?, ?, ?)",
                        getClusterId(), getMemberKey(), address, source, nodeId, now, now);
            }
            jdbc.update("delete from agent_trust where cluster_id = ? and last_seen < ?",
                    getClusterId(), now - MEMBER_LEASE_MILLIS);
        });
    }

    /**
     * 心跳时登记被控端回传的对端地址：被控端说看到的主控出口，与主控自报不一致就记一笔
     */
    public void observeAgentReport(Integer nodeId, JSONObject result) {
        if (result == null || !Integer.valueOf(200).equals(result.getInteger("code"))) {
            return;
        }
        JSONObject data = result.getJSONObject("data");
        if (data == null) {
            return;
        }
        List<String> reported = toStringList(data.getJSONArray("probes"));
        if (reported.isEmpty()) {
            return;
        }
        if (!memberAddresses.containsKey(getMemberKey())) {
            if (localAddresses.isEmpty()) {
                localAddresses = detectLocalAddresses();
            }
            memberAddresses.put(getMemberKey(), new LinkedHashSet<>(splitAddresses(localAddresses)));
        }
        storeAddresses("probe", nodeId, new LinkedHashSet<>(reported));
        Set<String> local = splitAddresses(localAddresses);
        List<String> missing = new ArrayList<>();
        for (String address : reported) {
            if (local.contains(address) || clientReportedAddresses.contains(address)) {
                continue;
            }
            missing.add(address);
        }
        if (!missing.isEmpty()) {
            clientReportedAddresses.addAll(missing);
            UnifiedLogger.warn(UnifiedLogger.LogType.SYSTEM,
                    "[Whitelist] 节点[{}]看到的主控出口与主控自报不一致: 节点看到={}, 主控自报={}。已按节点实测地址放行，请检查是否有SNAT/反向代理",
                    nodeId, String.join(",", missing), localAddresses);
        }
    }

    /**
     * 节点实测到的对端地址：被控端连过来时看到的来源，通常就是 SNAT 后的主控出口
     */
    private final Set<String> clientReportedAddresses = ConcurrentHashMap.newKeySet();

    /**
     * 集群内所有主控出口地址：各成员自报 + 被控端实测到的
     */
    public List<String> collectNetworks() {
        Set<String> networks = new LinkedHashSet<>();
        if (memberAddresses.isEmpty() && localAddresses.isEmpty()) {
            // 未走启动同步时（如单元测试或手触发的补下发）现探一次，空白名单会把主控挡在门外
            localAddresses = detectLocalAddresses();
            memberAddresses.put(getMemberKey(), new LinkedHashSet<>(splitAddresses(localAddresses)));
        }
        for (Set<String> addresses : memberAddresses.values()) {
            networks.addAll(addresses);
        }
        networks.addAll(jdbc.queryForList("select distinct address from agent_trust where cluster_id = ? and last_seen >= ? order by address",
                String.class, getClusterId(), System.currentTimeMillis() - MEMBER_LEASE_MILLIS));
        return new ArrayList<>(networks);
    }

    /**
     * 管理端查看当前生效的可信来源
     */
    public Map<String, Object> describe() {
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("clusterId", getClusterId());
        description.put("localAddresses", localAddresses.isEmpty() ? detectLocalAddresses() : localAddresses);
        description.put("probedSources", new LinkedHashMap<>(probedAddresses));
        description.put("probeErrors", new LinkedHashMap<>(probeErrors));
        description.put("networks", collectNetworks());
        description.put("lastFullSyncAt", lastFullSyncAt);
        description.put("syncedNodes", new ArrayList<>(syncedAt.keySet()));
        description.put("pendingNodes", new ArrayList<>(nextRetryAt.keySet()));
        return description;
    }

    private boolean syncNode(Master node, String token, String cluster) {
        try {
            List<String> networks = collectNetworks();
            JSONObject result = ClientApiUtil.syncTrustedNetworks(node.getHost(), node.getControllerPort(), token,
                    networks, cluster, SYNC_TIMEOUT_MILLIS);
            if (result != null && Integer.valueOf(200).equals(result.getInteger("code"))) {
                observeAgentReport(node.getId(), result);
                syncedAt.put(node.getId(), System.currentTimeMillis());
                nextRetryAt.remove(node.getId());
                return true;
            }
        } catch (Exception e) {
            // 单节点或探测失败不能中断其他节点的补偿。
        }
        nextRetryAt.put(node.getId(), System.currentTimeMillis() + RETRY_INTERVAL_MILLIS);
        UnifiedLogger.warn(UnifiedLogger.LogType.SYSTEM, "[Whitelist] 节点[{}]白名单同步失败，稍后重试", node.getName());
        return false;
    }

    private List<Master> listAgentNodes() {
        QueryWrapper<Master> queryWrapper = new QueryWrapper<>();
        queryWrapper.ne("status", 2);
        List<Master> nodes = masterService.list(queryWrapper);
        List<Master> result = new ArrayList<>();
        for (Master node : nodes) {
            if (node == null || node.getHost() == null || node.getControllerPort() == null) {
                continue;
            }
            result.add(node);
        }
        return result;
    }

    /**
     * 主控自身出口地址。
     * <p>
     * 先用公共探测源并发探一次，多个源报告的地址合并后全部下发——主控出口可能是多源，
     * 只取一个会漏。探测全挂（机房封了这些域名、或断外网）时才退化为本地网卡地址，
     * 保证白名单不会因为探测失败变成空。
     */
    private String detectLocalAddresses() {
        Set<String> addresses = new LinkedHashSet<>();
        PublicIpProbe.Result result = probePublicAddresses();
        for (String address : result.getAddresses()) {
            addresses.add(address + "/32");
        }
        if (addresses.isEmpty()) {
            addresses.addAll(detectInterfaceAddresses());
        }
        return String.join(" ", addresses);
    }

    /**
     * 探测公共 IP：结果写回 probedAddresses / probeErrors 供管理端查看
     */
    private PublicIpProbe.Result probePublicAddresses() {
        PublicIpProbe.Result result = PublicIpProbe.probe(this::fetchProbeBody);
        probedAddresses.clear();
        probedAddresses.putAll(result.getSources());
        probeErrors.clear();
        probeErrors.putAll(result.getErrors());
        if (result.isEmpty()) {
            UnifiedLogger.warn(UnifiedLogger.LogType.SYSTEM,
                    "[Whitelist] 公共IP探测源全部失败，退回本机网卡地址: " + result.getErrors());
        } else if (!result.getErrors().isEmpty()) {
            UnifiedLogger.log(UnifiedLogger.LogType.SYSTEM,
                    "[Whitelist] 部分公共IP探测源失败: " + result.getErrors() + ", 已获取: " + result.getAddresses());
        }
        return result;
    }

    private String fetchProbeBody(PublicIpProbe.Source source) {
        HttpHeaders headers = new HttpHeaders();
        // 有的源对空 UA 会直接返回空体或被风控
        headers.add(HttpHeaders.USER_AGENT, PROBE_USER_AGENT);
        headers.add(HttpHeaders.ACCEPT, "text/plain,*/*");
        ResponseEntity<String> response = PROBE_TEMPLATE.exchange(source.getUrl(), HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        return response.getBody();
    }

    /**
     * 本地网卡地址：探测不通时的兜底，容器里拿到的是 SNAT 前的私网地址，不一定有用
     */
    List<String> detectInterfaceAddresses() {
        Set<String> addresses = new LinkedHashSet<>();
        String routed = detectRouteAddress();
        if (routed != null && !routed.isEmpty()) {
            addresses.add(routed);
        }
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        addresses.add(address.getHostAddress() + "/32");
                    }
                }
            }
        } catch (Exception ignored) {
            // 拿不到接口信息时只用路由探测结果
        }
        return new ArrayList<>(addresses);
    }

    /**
     * 通过一次 UDP connect 拿默认出口地址：不实际发包，容器里也能拿到 SNAT 前的私网地址
     */
    private String detectRouteAddress() {
        try (DatagramSocket socket = new DatagramSocket()) {
            // UDP connect 只设置默认路由，不发包；拿到的就是出口网卡地址
            socket.connect(InetAddress.getByName("223.5.5.5"), 53);
            String address = socket.getLocalAddress().getHostAddress();
            return address == null || address.isEmpty() ? "" : address + "/32";
        } catch (Exception e) {
            return "";
        }
    }

    private Set<String> splitAddresses(String addresses) {
        Set<String> result = new LinkedHashSet<>();
        if (addresses == null || addresses.trim().isEmpty()) {
            return result;
        }
        for (String address : addresses.trim().split("[,\\s]+")) {
            if (!address.isEmpty()) {
                result.add(address);
            }
        }
        return result;
    }

    private List<String> toStringList(JSONArray array) {
        List<String> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (Object item : array) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private synchronized String getMemberKey() {
        if (memberId != null) {
            return memberId;
        }
        String resolved;
        if (configuredMemberId != null && !configuredMemberId.isBlank()) {
            resolved = configuredMemberId.trim();
        } else {
            Path path = Path.of("config", "whitelist-member-id");
            try {
                Files.createDirectories(path.getParent());
                try {
                    Files.writeString(path, UUID.randomUUID().toString(), StandardOpenOption.CREATE_NEW);
                } catch (FileAlreadyExistsException ignored) {
                    // 重启继续使用本实例身份；主备各自保存独立配置目录。
                }
                resolved = Files.readString(path).trim();
            } catch (IOException e) {
                throw new IllegalStateException("无法保存主控白名单成员标识");
            }
        }
        if (!resolved.matches("[a-zA-Z0-9_.-]{3,64}")) {
            throw new IllegalStateException("主控白名单成员标识无效");
        }
        memberId = resolved;
        return memberId;
    }

    /**
     * 集群标识优先显式配置，否则由共享通信密钥派生，不依赖各实例 JDBC 连接地址。
     */
    public String getClusterId() {
        if (clusterId == null) {
            clusterId = resolveClusterId();
        }
        return clusterId;
    }

    private String resolveClusterId() {
        if (configuredClusterId != null && !configuredClusterId.isBlank()) {
            String configured = configuredClusterId.trim().toLowerCase(java.util.Locale.ROOT);
            if (!configured.matches("[a-z0-9][a-z0-9_.-]{2,63}")) {
                throw new IllegalStateException("白名单集群标识无效");
            }
            return configured;
        }
        String seed = configService.getToken();
        if (seed == null || seed.isBlank()) {
            seed = datasourceUrl == null ? "qimenidc" : datasourceUrl.trim();
        }
        if (seed.isEmpty()) {
            return "qimenidc";
        }
        // 只下发摘要，通信密钥不会出现在请求参数和日志中。
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder("db-");
            for (int i = 0; i < 6; i++) {
                builder.append(String.format("%02x", digest[i]));
            }
            return builder.toString();
        } catch (Exception e) {
            return "qimenidc";
        }
    }
}
