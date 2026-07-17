package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.NatForwardRule;
import com.chuqiyun.proxmoxveams.entity.NatSyncState;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.NatForwardRuleService;
import com.chuqiyun.proxmoxveams.service.NatForwardSyncService;
import com.chuqiyun.proxmoxveams.service.NatSyncStateService;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Locale;

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

    @Override
    public synchronized boolean savePortRule(Integer nodeId, Integer hostId, String sourceIp, Integer sourcePort,
                                             String destinationIp, Integer destinationPort, String protocol) {
        String normalizedProtocol = normalizeProtocol(protocol);
        String ruleKey = buildPortRuleKey(nodeId, sourceIp, sourcePort, normalizedProtocol);
        NatForwardRule rule = getRuleByKey(ruleKey);
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
        natForwardRuleService.remove(new QueryWrapper<NatForwardRule>()
                .eq("rule_key", buildPortRuleKey(nodeId, sourceIp, sourcePort, normalizeProtocol(protocol))));
        return true;
    }

    @Override
    public synchronized boolean saveIpForwardRule(Integer nodeId, Integer hostId, String sourceIp, String destinationIp) {
        String ruleKey = buildIpRuleKey(nodeId, sourceIp);
        NatForwardRule rule = getRuleByKey(ruleKey);
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
        natForwardRuleService.remove(new QueryWrapper<NatForwardRule>()
                .eq("rule_key", buildIpRuleKey(nodeId, sourceIp)));
        return true;
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
            syncNode(node);
        }
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
