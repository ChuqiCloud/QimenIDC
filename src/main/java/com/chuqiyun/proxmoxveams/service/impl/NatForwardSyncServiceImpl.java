package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.NatForwardRule;
import com.chuqiyun.proxmoxveams.entity.NatSyncState;
import com.chuqiyun.proxmoxveams.entity.VpcIpBinding;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.NatForwardRuleService;
import com.chuqiyun.proxmoxveams.service.NatForwardSyncService;
import com.chuqiyun.proxmoxveams.service.NatSyncStateService;
import com.chuqiyun.proxmoxveams.service.VpcIpBindingService;
import com.chuqiyun.proxmoxveams.service.VmIdReservationService;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * @Author: 星禾
 * @Description: 主控NAT规则首次导入与权威同步
 * @DateTime: 2026/7/17 06:06
 */
@Slf4j
@Service("natForwardSyncService")
@EnableScheduling
public class NatForwardSyncServiceImpl implements NatForwardSyncService {
    private static final String RULE_TYPE_PORT = "port";
    private static final String RULE_TYPE_IP = "ip";

    @Resource
    private NatForwardRuleService natForwardRuleService;
    @Resource
    private NatSyncStateService natSyncStateService;
    @Resource
    private MasterService masterService;
    @Resource
    private ConfigService configService;
    @Resource
    private VpcIpBindingService vpcIpBindingService;
    @Resource
    private VmIdReservationService vmIdReservationService;

    @Override
    public synchronized boolean savePortRule(Integer nodeId, Integer hostId, String sourceIp, Integer sourcePort,
                                             String destinationIp, Integer destinationPort, String protocol) {
        String normalizedProtocol = normalizeProtocol(protocol);
        String ruleKey = buildPortRuleKey(nodeId, sourceIp, sourcePort, normalizedProtocol);
        NatForwardRule rule = getRuleByKey(ruleKey);
        if (rule != null && (!Objects.equals(rule.getHostId(), hostId)
                || !Objects.equals(rule.getDestinationIp(), normalize(destinationIp))
                || !Objects.equals(rule.getDestinationPort(), destinationPort))) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (rule == null) {
            rule = new NatForwardRule();
            rule.setRuleKey(ruleKey);
            rule.setRuleType(RULE_TYPE_PORT);
            rule.setCreateTime(now);
        }
        rule.setNodeId(nodeId);
        rule.setHostId(hostId);
        rule.setSourceIp(normalize(sourceIp));
        rule.setSourcePort(sourcePort);
        rule.setDestinationIp(normalize(destinationIp));
        rule.setDestinationPort(destinationPort);
        rule.setProtocol(normalizedProtocol);
        rule.setUpdateTime(now);
        return rule.getId() == null ? natForwardRuleService.save(rule) : natForwardRuleService.updateById(rule);
    }

    @Override
    public synchronized boolean deletePortRule(Integer nodeId, String sourceIp, Integer sourcePort, String protocol) {
        String ruleKey = buildPortRuleKey(nodeId, sourceIp, sourcePort, normalizeProtocol(protocol));
        natForwardRuleService.remove(new QueryWrapper<NatForwardRule>().eq("rule_key", ruleKey));
        return getRuleByKey(ruleKey) == null;
    }

    @Override
    public synchronized boolean saveIpForwardRule(Integer nodeId, Integer hostId, String sourceIp, String destinationIp) {
        String ruleKey = buildIpRuleKey(nodeId, sourceIp);
        NatForwardRule rule = getRuleByKey(ruleKey);
        if (rule != null && (!Objects.equals(rule.getHostId(), hostId)
                || !Objects.equals(rule.getDestinationIp(), normalize(destinationIp)))) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (rule == null) {
            rule = new NatForwardRule();
            rule.setRuleKey(ruleKey);
            rule.setRuleType(RULE_TYPE_IP);
            rule.setCreateTime(now);
        }
        rule.setNodeId(nodeId);
        rule.setHostId(hostId);
        rule.setSourceIp(normalize(sourceIp));
        rule.setDestinationIp(normalize(destinationIp));
        rule.setUpdateTime(now);
        return rule.getId() == null ? natForwardRuleService.save(rule) : natForwardRuleService.updateById(rule);
    }

    @Override
    public synchronized boolean deleteIpForwardRule(Integer nodeId, String sourceIp) {
        String ruleKey = buildIpRuleKey(nodeId, sourceIp);
        natForwardRuleService.remove(new QueryWrapper<NatForwardRule>().eq("rule_key", ruleKey));
        return getRuleByKey(ruleKey) == null;
    }

    @Override
    public long countPortRules(Integer hostId) {
        return natForwardRuleService.count(new QueryWrapper<NatForwardRule>()
                .eq("host_id", hostId)
                .eq("rule_type", RULE_TYPE_PORT));
    }

    @Override
    public JSONArray getPortRulesByHost(Integer hostId, int page, int size) {
        Page<NatForwardRule> rulePage = new Page<>(Math.max(page, 1), Math.max(size, 1));
        natForwardRuleService.page(rulePage, new QueryWrapper<NatForwardRule>()
                .eq("host_id", hostId)
                .eq("rule_type", RULE_TYPE_PORT)
                .orderByAsc("source_port", "protocol"));
        JSONArray result = new JSONArray();
        for (NatForwardRule rule : rulePage.getRecords()) {
            result.add(toPortRuleJson(rule));
        }
        return result;
    }

    @Override
    @Scheduled(initialDelay = 30_000, fixedDelay = 5 * 60 * 1000)
    public void syncAllNodes() {
        for (Master node : masterService.list()) {
            if (node == null || node.getId() == null || StringUtils.isBlank(node.getHost()) || node.getControllerPort() == null) {
                continue;
            }
            vmIdReservationService.withAllocationLock(() -> {
                synchronized (NatForwardSyncService.class) {
                    if (vmIdReservationService.isNodeInMigration(node.getId())) {
                        // 迁移逐条清理期间不能将旧数据库快照同步回节点，失败任务也继续保留屏障。
                        return;
                    }
                    syncNode(node);
                    // NAT 同步不会补齐历史 VPC 公网地址。节点升级或公网接入
                    // 模式切换后，独立执行一次幂等 ensure，修复“规则存在但
                    // vmbr0 没有 /32 地址”的旧绑定。
                    try {
                        reconcileVpcPublicIpAddresses(node);
                    } catch (Exception e) {
                        // 补偿失败不能阻断其他节点的 NAT 同步；下一个周期继续重试。
                        log.warn("[VPC-IP-RECONCILE] 节点补偿任务异常: NodeId={}, Error={}",
                                node.getId(), e.getMessage());
                    }
                }
            });
        }
    }

    private void reconcileVpcPublicIpAddresses(Master node) {
        if (node == null || node.getId() == null || StringUtils.isBlank(node.getHost())
                || node.getControllerPort() == null || StringUtils.isBlank(node.getPublicIpInterface())) {
            return;
        }
        String mode = normalizeVpcPublicIpMode(node.getPublicIpMode(), node.getPublicIpInterface());
        if ("legacy".equals(mode)) {
            return;
        }
        List<NatForwardRule> rules = natForwardRuleService.list(new QueryWrapper<NatForwardRule>()
                .eq("node_id", node.getId())
                .eq("rule_type", RULE_TYPE_IP)
                .orderByAsc("id"));
        if (rules == null || rules.isEmpty()) {
            return;
        }
        for (NatForwardRule rule : rules) {
            if (rule == null || StringUtils.isBlank(rule.getSourceIp())) {
                continue;
            }
            try {
                JSONObject result = ClientApiUtil.ensureVpcPublicIp(node.getHost(), configService.getToken(),
                        node.getControllerPort(), rule.getSourceIp(), mode, node.getPublicIpInterface());
                if (result == null || !Objects.equals(result.getInteger("code"), 200)) {
                    updateVpcBindingState(rule, null, abbreviate(result == null ? "QAgent无响应" : result.getString("message")));
                    log.warn("[VPC-IP-RECONCILE] 公网地址补偿失败: NodeId={}, PublicIp={}, Mode={}, Result={}",
                            node.getId(), rule.getSourceIp(), mode, result);
                } else {
                    updateVpcBindingState(rule, result, null);
                    log.debug("[VPC-IP-RECONCILE] 公网地址已确认: NodeId={}, PublicIp={}, Interface={}, Mode={}",
                            node.getId(), rule.getSourceIp(), node.getPublicIpInterface(), mode);
                }
            } catch (Exception e) {
                updateVpcBindingState(rule, null, abbreviate(e.getMessage()));
                log.warn("[VPC-IP-RECONCILE] 公网地址补偿异常: NodeId={}, PublicIp={}, Error={}",
                        node.getId(), rule.getSourceIp(), e.getMessage());
            }
        }
    }

    private void updateVpcBindingState(NatForwardRule rule, JSONObject result, String error) {
        if (rule == null || rule.getHostId() == null || StringUtils.isBlank(rule.getSourceIp())
                || vpcIpBindingService == null) {
            return;
        }
        try {
            VpcIpBinding binding = vpcIpBindingService.getOne(new QueryWrapper<VpcIpBinding>()
                    .eq("host_id", rule.getHostId())
                    .eq("public_ip", rule.getSourceIp())
                    .eq("status", 1)
                    .last("limit 1"));
            if (binding == null) {
                return;
            }
            long now = System.currentTimeMillis();
            binding.setLastCheckTime(now);
            binding.setUpdateTime(now);
            if (result != null) {
                binding.setDeliveryMode(StringUtils.defaultIfBlank(result.getString("mode"), binding.getDeliveryMode()));
                binding.setInterfaceName(StringUtils.defaultIfBlank(result.getString("interface"), binding.getInterfaceName()));
                if (Boolean.TRUE.equals(result.getBoolean("address_installed"))) {
                    binding.setAddressInstalled(1);
                }
                if (Boolean.TRUE.equals(result.getBoolean("route_installed"))) {
                    binding.setRouteInstalled(1);
                }
                binding.setLastError(null);
            } else {
                binding.setLastError(StringUtils.defaultIfBlank(error, "公网地址补偿失败"));
            }
            vpcIpBindingService.updateById(binding);
        } catch (Exception e) {
            log.warn("[VPC-IP-RECONCILE] 更新绑定状态失败: HostId={}, PublicIp={}, Error={}",
                    rule.getHostId(), rule.getSourceIp(), e.getMessage());
        }
    }

    private String normalizeVpcPublicIpMode(String mode, String interfaceName) {
        String normalized = StringUtils.isBlank(mode) ? "legacy" : mode.trim().toLowerCase(Locale.ROOT);
        if ("auto".equals(normalized)) {
            return StringUtils.isBlank(interfaceName) ? "legacy" : "l2_alias";
        }
        if ("legacy".equals(normalized) && StringUtils.isNotBlank(interfaceName)) {
            return "l2_alias";
        }
        return Objects.equals(normalized, "routed") || Objects.equals(normalized, "l2_alias")
                || Objects.equals(normalized, "proxy_arp") ? normalized : "legacy";
    }

    private void syncNode(Master node) {
        NatSyncState state = getOrCreateState(node.getId());
        try {
            if (!Integer.valueOf(1).equals(state.getInitialImported())) {
                if (!importNodeRules(node)) {
                    updateState(state, false, "首次导入失败，将在下次任务重试", false);
                    return;
                }
                state.setInitialImported(1);
                state.setLastImportTime(System.currentTimeMillis());
                natSyncStateService.updateById(state);
            }

            List<NatForwardRule> rules = natForwardRuleService.list(new QueryWrapper<NatForwardRule>()
                    .eq("node_id", node.getId())
                    .orderByAsc("id"));
            JSONArray portRules = new JSONArray();
            JSONArray ipForwardRules = new JSONArray();
            for (NatForwardRule rule : rules) {
                if (RULE_TYPE_PORT.equals(rule.getRuleType())) {
                    portRules.add(toPortRuleJson(rule));
                } else if (RULE_TYPE_IP.equals(rule.getRuleType())) {
                    ipForwardRules.add(toIpForwardRuleJson(rule));
                }
            }
            boolean synced = ClientApiUtil.syncNatRules(node.getHost(), configService.getToken(),
                    node.getControllerPort(), portRules, ipForwardRules);
            updateState(state, synced, synced ? "同步成功" : "宿主机拒绝同步", true);
        } catch (Exception e) {
            updateState(state, false, abbreviate(e.getMessage()), true);
            log.warn("[NAT-SYNC] 节点同步失败 nodeId={}, host={}", node.getId(), node.getHost(), e);
        }
    }

    private boolean importNodeRules(Master node) {
        JSONObject response = ClientApiUtil.exportNatRules(node.getHost(), configService.getToken(), node.getControllerPort());
        if (response == null || response.getJSONObject("data") == null) {
            return false;
        }
        JSONObject data = response.getJSONObject("data");
        JSONArray portRules = data.getJSONArray("port_rules");
        JSONArray ipForwardRules = data.getJSONArray("ip_forward_rules");
        if (portRules != null) {
            for (JSONObject rule : portRules.toJavaList(JSONObject.class)) {
                if (!savePortRule(node.getId(), rule.getInteger("vm"), rule.getString("source_ip"),
                        rule.getInteger("source_port"), rule.getString("destination_ip"),
                        rule.getInteger("destination_port"), rule.getString("protocol"))) {
                    return false;
                }
            }
        }
        if (ipForwardRules != null) {
            for (JSONObject rule : ipForwardRules.toJavaList(JSONObject.class)) {
                if (!saveIpForwardRule(node.getId(), rule.getInteger("vm"), rule.getString("source_ip"),
                        rule.getString("destination_ip"))) {
                    return false;
                }
            }
        }
        log.info("[NAT-SYNC] 节点首次导入完成 nodeId={}, portRules={}, ipForwardRules={}", node.getId(),
                portRules == null ? 0 : portRules.size(), ipForwardRules == null ? 0 : ipForwardRules.size());
        return true;
    }

    private NatSyncState getOrCreateState(Integer nodeId) {
        NatSyncState state = natSyncStateService.getOne(new QueryWrapper<NatSyncState>()
                .eq("node_id", nodeId)
                .last("limit 1"));
        if (state != null) {
            return state;
        }
        state = new NatSyncState();
        state.setNodeId(nodeId);
        state.setInitialImported(0);
        state.setInSync(0);
        natSyncStateService.save(state);
        return state;
    }

    private void updateState(NatSyncState state, boolean inSync, String message, boolean syncAttempted) {
        state.setInSync(inSync ? 1 : 0);
        state.setLastMessage(abbreviate(message));
        if (syncAttempted) {
            state.setLastSyncTime(System.currentTimeMillis());
        }
        natSyncStateService.updateById(state);
    }

    private NatForwardRule getRuleByKey(String ruleKey) {
        return natForwardRuleService.getOne(new QueryWrapper<NatForwardRule>()
                .eq("rule_key", ruleKey)
                .last("limit 1"));
    }

    private JSONObject toPortRuleJson(NatForwardRule rule) {
        JSONObject json = new JSONObject();
        json.put("source_ip", rule.getSourceIp());
        json.put("source_port", rule.getSourcePort());
        json.put("destination_ip", rule.getDestinationIp());
        json.put("destination_port", rule.getDestinationPort());
        json.put("protocol", rule.getProtocol());
        json.put("vm", rule.getHostId());
        return json;
    }

    private JSONObject toIpForwardRuleJson(NatForwardRule rule) {
        JSONObject json = new JSONObject();
        json.put("source_ip", rule.getSourceIp());
        json.put("destination_ip", rule.getDestinationIp());
        json.put("vm", rule.getHostId());
        return json;
    }

    private String buildPortRuleKey(Integer nodeId, String sourceIp, Integer sourcePort, String protocol) {
        return "PORT|" + nodeId + "|" + normalize(sourceIp) + "|" + sourcePort + "|" + normalizeProtocol(protocol);
    }

    private String buildIpRuleKey(Integer nodeId, String sourceIp) {
        return "IP|" + nodeId + "|" + normalize(sourceIp);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeProtocol(String protocol) {
        return normalize(protocol).toLowerCase(Locale.ROOT);
    }

    private String abbreviate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 255 ? message : message.substring(0, 255);
    }
}
