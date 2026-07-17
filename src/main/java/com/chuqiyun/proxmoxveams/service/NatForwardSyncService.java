package com.chuqiyun.proxmoxveams.service;

import com.alibaba.fastjson2.JSONArray;

public interface NatForwardSyncService {
    boolean savePortRule(Integer nodeId, Integer hostId, String sourceIp, Integer sourcePort,
                         String destinationIp, Integer destinationPort, String protocol);

    boolean deletePortRule(Integer nodeId, String sourceIp, Integer sourcePort, String protocol);

    boolean saveIpForwardRule(Integer nodeId, Integer hostId, String sourceIp, String destinationIp);

    boolean deleteIpForwardRule(Integer nodeId, String sourceIp);

    long countPortRules(Integer hostId);

    JSONArray getPortRulesByHost(Integer hostId, int page, int size);

    void syncAllNodes();
}
