package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.VmResourceRankDao;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.VmResourceRank;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.VmResourceRankService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * VM resource rank service implementation.
 *
 * @author codex
 * @since 2026-05-29
 */
@Slf4j
@Service("vmResourceRankService")
public class VmResourceRankServiceImpl extends ServiceImpl<VmResourceRankDao, VmResourceRank> implements VmResourceRankService {
    private static final String RANK_TYPE_CPU = "cpu";
    private static final String RANK_TYPE_MEMORY = "memory";
    private static final int RANK_LIMIT = 10;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    @Resource
    private VmhostService vmhostService;
    @Resource
    private MasterService masterService;
    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public void refreshRank() {
        if (!refreshing.compareAndSet(false, true)) {
            log.warn("[VmResourceRank] previous refresh is still running, skip this round");
            return;
        }
        try {
            long collectTime = System.currentTimeMillis();
            List<VmResourceRank> resourceList = collectVmResource(collectTime);
            List<VmResourceRank> saveList = new ArrayList<>();

            resourceList.sort((item1, item2) -> Double.compare(item2.getCpu(), item1.getCpu()));
            for (int i = 0; i < resourceList.size() && i < RANK_LIMIT; i++) {
                saveList.add(copyRank(resourceList.get(i), RANK_TYPE_CPU, i + 1));
            }

            resourceList.sort((item1, item2) -> Long.compare(item2.getMemory(), item1.getMemory()));
            for (int i = 0; i < resourceList.size() && i < RANK_LIMIT; i++) {
                saveList.add(copyRank(resourceList.get(i), RANK_TYPE_MEMORY, i + 1));
            }

            transactionTemplate.executeWithoutResult(status -> {
                this.remove(new QueryWrapper<VmResourceRank>());
                if (!saveList.isEmpty()) {
                    this.saveBatch(saveList);
                }
            });
            log.info("[VmResourceRank] refresh rank success, total: {}", saveList.size());
        } finally {
            refreshing.set(false);
        }
    }

    @Override
    public List<VmResourceRank> getRank(String rankType) {
        return this.list(new QueryWrapper<VmResourceRank>()
                .eq("rank_type", rankType)
                .orderByAsc("rank_no")
                .last("limit " + RANK_LIMIT));
    }

    private List<VmResourceRank> collectVmResource(long collectTime) {
        List<VmResourceRank> resourceList = new ArrayList<>();
        List<Vmhost> vmhostList = vmhostService.list(new QueryWrapper<Vmhost>().eq("delete_state", 0));
        HashMap<Integer, Master> nodeMap = new HashMap<>();
        HashMap<Integer, HashMap<String, String>> cookieMap = new HashMap<>();
        HashMap<Integer, Set<Integer>> nodeVmIdMap = new HashMap<>();
        Set<Integer> failedNodeIds = new HashSet<>();
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();

        for (Vmhost vmhost : vmhostList) {
            try {
                Integer nodeId = vmhost.getNodeid();
                if (failedNodeIds.contains(nodeId)) {
                    continue;
                }

                Master node = nodeMap.get(vmhost.getNodeid());
                if (node == null) {
                    node = masterService.getById(vmhost.getNodeid());
                    if (node == null || node.getStatus() != 0) {
                        continue;
                    }
                    nodeMap.put(vmhost.getNodeid(), node);
                }

                HashMap<String, String> authentications = cookieMap.get(vmhost.getNodeid());
                if (authentications == null) {
                    authentications = masterService.getMasterCookieMap(vmhost.getNodeid());
                    cookieMap.put(vmhost.getNodeid(), authentications);
                }

                Set<Integer> existingVmIds = nodeVmIdMap.get(nodeId);
                if (existingVmIds == null) {
                    try {
                        existingVmIds = getExistingVmIds(node, authentications, proxmoxApiUtil);
                        nodeVmIdMap.put(nodeId, existingVmIds);
                    } catch (Exception e) {
                        failedNodeIds.add(nodeId);
                        log.warn("[VmResourceRank] load node vm list failed, nodeId: {}, nodeName: {}",
                                nodeId, node.getNodeName(), e);
                        continue;
                    }
                }

                if (!existingVmIds.contains(vmhost.getVmid())) {
                    log.info("[VmResourceRank] skip vm not found in proxmox, hostId: {}, nodeId: {}, nodeName: {}, vmid: {}, hostname: {}",
                            vmhost.getId(), nodeId, node.getNodeName(), vmhost.getVmid(), vmhost.getHostname());
                    continue;
                }

                JSONObject current = proxmoxApiUtil.getVmStatus(node, authentications, vmhost.getVmid());
                if (current == null) {
                    throw new IllegalStateException("Proxmox API returned empty vm status response");
                }
                JSONObject data = current.getJSONObject("data");
                if (data == null) {
                    data = current;
                }

                resourceList.add(buildRank(vmhost, node, data, collectTime));
            } catch (Exception e) {
                log.warn("[VmResourceRank] collect vm resource failed, hostId: {}, nodeId: {}, vmid: {}, hostname: {}",
                        vmhost.getId(), vmhost.getNodeid(), vmhost.getVmid(), vmhost.getHostname(), e);
            }
        }
        return resourceList;
    }

    private Set<Integer> getExistingVmIds(Master node, HashMap<String, String> authentications, ProxmoxApiUtil proxmoxApiUtil) {
        JSONObject vmJson = proxmoxApiUtil.getNodeApi(node, authentications, "/nodes/" + node.getNodeName() + "/qemu", new HashMap<>());
        if (vmJson == null) {
            throw new IllegalStateException("Proxmox API returned empty vm list response");
        }

        Set<Integer> vmIds = new HashSet<>();
        JSONArray vmList = vmJson.getJSONArray("data");
        if (vmList == null) {
            return vmIds;
        }

        for (int i = 0; i < vmList.size(); i++) {
            JSONObject vm = vmList.getJSONObject(i);
            if (vm != null && vm.containsKey("vmid")) {
                vmIds.add(vm.getInteger("vmid"));
            }
        }
        return vmIds;
    }

    private VmResourceRank buildRank(Vmhost vmhost, Master node, JSONObject data, long collectTime) {
        double cpu = data.getDoubleValue("cpu");
        long memory = data.getLongValue("mem");
        long maxMemory = data.getLongValue("maxmem");

        VmResourceRank rank = new VmResourceRank();
        rank.setHostId(vmhost.getId());
        rank.setVmid(vmhost.getVmid());
        rank.setHostname(vmhost.getHostname());
        rank.setNodeId(vmhost.getNodeid());
        rank.setNodeName(node.getName());
        rank.setCpu(cpu);
        rank.setCpuPercent(Math.round(cpu * 10000) / 100.0);
        rank.setMemory(memory);
        rank.setMemoryMb(Math.round(memory / 1024.0 / 1024.0 * 100) / 100.0);
        rank.setMaxMemory(maxMemory);
        rank.setMaxMemoryMb(Math.round(maxMemory / 1024.0 / 1024.0 * 100) / 100.0);
        rank.setMemoryPercent(maxMemory == 0 ? 0 : Math.round(memory * 10000.0 / maxMemory) / 100.0);
        rank.setCollectTime(collectTime);
        return rank;
    }

    private VmResourceRank copyRank(VmResourceRank source, String rankType, int rankNo) {
        VmResourceRank rank = new VmResourceRank();
        rank.setRankType(rankType);
        rank.setRankNo(rankNo);
        rank.setHostId(source.getHostId());
        rank.setVmid(source.getVmid());
        rank.setHostname(source.getHostname());
        rank.setNodeId(source.getNodeId());
        rank.setNodeName(source.getNodeName());
        rank.setCpu(source.getCpu());
        rank.setCpuPercent(source.getCpuPercent());
        rank.setMemory(source.getMemory());
        rank.setMemoryMb(source.getMemoryMb());
        rank.setMaxMemory(source.getMaxMemory());
        rank.setMaxMemoryMb(source.getMaxMemoryMb());
        rank.setMemoryPercent(source.getMemoryPercent());
        rank.setCollectTime(source.getCollectTime());
        return rank;
    }
}
