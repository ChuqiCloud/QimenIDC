package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.chuqiyun.proxmoxveams.dao.VmhostDao;
import com.chuqiyun.proxmoxveams.dto.SecurityGroupApplyDto;
import com.chuqiyun.proxmoxveams.dto.SecurityGroupBindParams;
import com.chuqiyun.proxmoxveams.dto.SecurityGroupRuleDto;
import com.chuqiyun.proxmoxveams.entity.*;
import com.chuqiyun.proxmoxveams.service.*;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @Author: 星禾
 * @Description: 安全组业务服务
 * @DateTime: 2026/7/1 22:20
 */
@Slf4j
@Service("securityGroupBusinessService")
public class SecurityGroupBusinessServiceImpl implements SecurityGroupBusinessService {
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_DELETED = 0;
    private static final String NETWORK_TYPE_VPC = "vpc";

    @Resource
    private SecurityGroupService securityGroupService;
    @Resource
    private SecurityGroupRuleService securityGroupRuleService;
    @Resource
    private SecurityGroupBindingService securityGroupBindingService;
    @Resource
    private SecurityGroupSyncService securityGroupSyncService;
    @Resource
    private VmhostDao vmhostDao;
    @Resource
    private MasterService masterService;
    @Resource
    private IppoolService ippoolService;
    @Resource
    private VpcIpBindingService vpcIpBindingService;
    @Resource
    private ConfigService configService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean bindVm(SecurityGroupBindParams params) {
        if (params == null || params.getHostId() == null) {
            return false;
        }
        Vmhost vmhost = getVmhostById(params.getHostId());
        if (vmhost == null) {
            return false;
        }
        List<Integer> groupIds = normalizeGroupIds(params);
        if (groupIds.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        for (Integer groupId : groupIds) {
            SecurityGroup group = securityGroupService.getById(groupId);
            if (group == null || Objects.equals(group.getStatus(), STATUS_DELETED) || !isGroupVisibleToHost(group, vmhost.getId())) {
                return false;
            }
            SecurityGroupBinding oldBinding = getBinding(params.getHostId(), groupId);
            if (oldBinding != null) {
                oldBinding.setStatus(STATUS_ACTIVE);
                oldBinding.setUpdateTime(now);
                securityGroupBindingService.updateById(oldBinding);
                continue;
            }
            SecurityGroupBinding binding = new SecurityGroupBinding();
            binding.setGroupId(groupId);
            binding.setHostId(vmhost.getId());
            binding.setVmId(vmhost.getVmid());
            binding.setNodeId(vmhost.getNodeid());
            binding.setNetworkType(StringUtils.defaultIfBlank(vmhost.getNetworkType(), "classic"));
            binding.setStatus(STATUS_ACTIVE);
            binding.setCreateTime(now);
            binding.setUpdateTime(now);
            securityGroupBindingService.save(binding);
        }
        boolean synced = Boolean.TRUE.equals(syncVm(params.getHostId()));
        return syncGroupsReferencing(groupIds) && synced;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean unbindVm(SecurityGroupBindParams params) {
        if (params == null || params.getHostId() == null) {
            return false;
        }
        List<Integer> groupIds = normalizeGroupIds(params);
        List<Integer> affectedGroupIds = groupIds.isEmpty() ? getBoundGroupIds(params.getHostId()) : groupIds;
        UpdateWrapper<SecurityGroupBinding> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("host_id", params.getHostId());
        updateWrapper.eq("status", STATUS_ACTIVE);
        if (!groupIds.isEmpty()) {
            updateWrapper.in("group_id", groupIds);
        }
        updateWrapper.set("status", STATUS_DELETED);
        updateWrapper.set("update_time", System.currentTimeMillis());
        securityGroupBindingService.update(updateWrapper);
        List<Integer> remainGroupIds = getBoundGroupIds(params.getHostId());
        Boolean referencedSynced = syncGroupsReferencing(affectedGroupIds);
        if (remainGroupIds.isEmpty()) {
            return Boolean.TRUE.equals(deleteVmRules(params.getHostId())) && Boolean.TRUE.equals(referencedSynced);
        }
        return Boolean.TRUE.equals(syncVm(params.getHostId())) && Boolean.TRUE.equals(referencedSynced);
    }

    @Override
    public Boolean syncVm(Integer hostId) {
        SecurityGroupApplyDto applyDto = buildApplyDto(hostId);
        if (applyDto == null) {
            return deleteVmRules(hostId);
        }
        Vmhost vmhost = getVmhostById(hostId);
        Master node = vmhost == null ? null : masterService.getById(vmhost.getNodeid());
        if (node == null) {
            return false;
        }
        String hash = buildRuleHash(applyDto);
        Boolean result = ClientApiUtil.applySecurityGroup(node.getHost(), configService.getToken(), node.getControllerPort(), applyDto);
        saveSyncState(vmhost, hash, Boolean.TRUE.equals(result), Boolean.TRUE.equals(result) ? "同步成功" : "同步失败");
        return result;
    }

    @Override
    public Boolean syncGroup(Integer groupId) {
        if (groupId == null) {
            return false;
        }
        Set<Integer> hostIds = getHostIdsByGroupId(groupId);
        hostIds.addAll(getHostIdsByReferencingGroupId(groupId));
        boolean result = true;
        for (Integer hostId : hostIds) {
            result = Boolean.TRUE.equals(syncVm(hostId)) && result;
        }
        return result;
    }

    @Override
    public Boolean syncGroupsReferencing(List<Integer> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return true;
        }
        Set<Integer> hostIds = new LinkedHashSet<>();
        for (Integer groupId : groupIds) {
            hostIds.addAll(getHostIdsByReferencingGroupId(groupId));
        }
        boolean result = true;
        for (Integer hostId : hostIds) {
            result = Boolean.TRUE.equals(syncVm(hostId)) && result;
        }
        return result;
    }

    @Override
    public Boolean deleteVmRules(Integer hostId) {
        Vmhost vmhost = getVmhostById(hostId);
        if (vmhost == null) {
            return false;
        }
        Master node = masterService.getById(vmhost.getNodeid());
        if (node == null) {
            return false;
        }
        Boolean result = ClientApiUtil.deleteSecurityGroup(node.getHost(), configService.getToken(), node.getControllerPort(), hostId);
        if (Boolean.TRUE.equals(result)) {
            saveSyncState(vmhost, "", true, "已删除安全组规则");
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteVmSecurityGroups(Integer hostId) {
        if (hostId == null) {
            return false;
        }
        Boolean ruleDeleted = deleteVmRules(hostId);
        long now = System.currentTimeMillis();
        QueryWrapper<SecurityGroup> groupQueryWrapper = new QueryWrapper<>();
        groupQueryWrapper.eq("host_id", hostId);
        groupQueryWrapper.eq("status", STATUS_ACTIVE);
        List<SecurityGroup> groups = securityGroupService.list(groupQueryWrapper);
        List<Integer> groupIds = groups.stream().map(SecurityGroup::getId).collect(Collectors.toList());
        if (!groupIds.isEmpty()) {
            UpdateWrapper<SecurityGroup> groupUpdateWrapper = new UpdateWrapper<>();
            groupUpdateWrapper.in("id", groupIds);
            groupUpdateWrapper.set("status", STATUS_DELETED);
            groupUpdateWrapper.set("update_time", now);
            securityGroupService.update(groupUpdateWrapper);

            UpdateWrapper<SecurityGroupRule> ruleUpdateWrapper = new UpdateWrapper<>();
            ruleUpdateWrapper.in("group_id", groupIds);
            ruleUpdateWrapper.set("status", STATUS_DELETED);
            ruleUpdateWrapper.set("update_time", now);
            securityGroupRuleService.update(ruleUpdateWrapper);
        }
        UpdateWrapper<SecurityGroupBinding> bindingUpdateWrapper = new UpdateWrapper<>();
        bindingUpdateWrapper.eq("host_id", hostId);
        bindingUpdateWrapper.set("status", STATUS_DELETED);
        bindingUpdateWrapper.set("update_time", now);
        securityGroupBindingService.update(bindingUpdateWrapper);
        return Boolean.TRUE.equals(ruleDeleted) || !groupIds.isEmpty();
    }

    @Override
    public JSONObject checkVm(Integer hostId) {
        JSONObject result = new JSONObject();
        SecurityGroupApplyDto applyDto = buildApplyDto(hostId);
        String expectedHash = applyDto == null ? "" : buildRuleHash(applyDto);
        Vmhost vmhost = getVmhostById(hostId);
        Master node = vmhost == null ? null : masterService.getById(vmhost.getNodeid());
        if (node == null) {
            result.put("inSync", false);
            result.put("message", "节点不存在");
            return result;
        }
        JSONObject agentResult = ClientApiUtil.checkSecurityGroup(node.getHost(), configService.getToken(), node.getControllerPort(), hostId);
        String actualHash = agentResult == null ? null : agentResult.getString("ruleHash");
        boolean inSync = StringUtils.equals(expectedHash, actualHash);
        result.put("hostId", hostId);
        result.put("expectedHash", expectedHash);
        result.put("actualHash", actualHash);
        result.put("inSync", inSync);
        result.put("agent", agentResult);
        if (vmhost != null) {
            saveSyncState(vmhost, expectedHash, inSync, inSync ? "规则一致" : "规则不一致");
        }
        return result;
    }

    @Override
    public List<Integer> getBoundGroupIds(Integer hostId) {
        QueryWrapper<SecurityGroupBinding> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("host_id", hostId);
        queryWrapper.eq("status", STATUS_ACTIVE);
        queryWrapper.orderByAsc("id");
        return securityGroupBindingService.list(queryWrapper).stream()
                .map(SecurityGroupBinding::getGroupId)
                .collect(Collectors.toList());
    }

    private SecurityGroupApplyDto buildApplyDto(Integer hostId) {
        Vmhost vmhost = getVmhostById(hostId);
        if (vmhost == null) {
            return null;
        }
        List<Integer> groupIds = getBoundGroupIds(hostId);
        if (groupIds.isEmpty()) {
            return null;
        }
        List<String> targetIps = getTargetIps(vmhost);
        if (targetIps.isEmpty()) {
            return null;
        }
        SecurityGroupApplyDto applyDto = new SecurityGroupApplyDto();
        applyDto.setHostId(vmhost.getId());
        applyDto.setVmId(vmhost.getVmid());
        applyDto.setNetworkType(StringUtils.defaultIfBlank(vmhost.getNetworkType(), "classic"));
        applyDto.setTargetIps(targetIps);
        applyDto.setDefaultIngressAction(resolveDefaultAction(groupIds, "ingress"));
        applyDto.setDefaultEgressAction(resolveDefaultAction(groupIds, "egress"));
        applyDto.setRules(buildEffectiveRules(groupIds, new LinkedHashSet<>()));
        return applyDto;
    }

    private List<SecurityGroupRuleDto> buildEffectiveRules(List<Integer> groupIds, Set<Integer> visitedGroupIds) {
        List<SecurityGroupRuleDto> rules = new ArrayList<>();
        for (Integer groupId : groupIds) {
            if (groupId == null || visitedGroupIds.contains(groupId)) {
                continue;
            }
            visitedGroupIds.add(groupId);
            QueryWrapper<SecurityGroupRule> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("group_id", groupId);
            queryWrapper.eq("status", STATUS_ACTIVE);
            queryWrapper.orderByAsc("priority", "id");
            List<SecurityGroupRule> groupRules = securityGroupRuleService.list(queryWrapper);
            for (SecurityGroupRule rule : groupRules) {
                if (rule.getRemoteGroupId() != null) {
                    SecurityGroupRuleDto dto = toRuleDto(rule);
                    dto.setRemoteIps(getSecurityGroupTargetIps(rule.getRemoteGroupId()));
                    rules.add(dto);
                } else {
                    rules.add(toRuleDto(rule));
                }
            }
        }
        rules.sort(Comparator.comparing(SecurityGroupRuleDto::getPriority, Comparator.nullsLast(Integer::compareTo)));
        return rules;
    }

    private SecurityGroupRuleDto toRuleDto(SecurityGroupRule rule) {
        SecurityGroupRuleDto dto = new SecurityGroupRuleDto();
        dto.setDirection(StringUtils.defaultIfBlank(rule.getDirection(), "ingress"));
        dto.setProtocol(StringUtils.defaultIfBlank(rule.getProtocol(), "all"));
        dto.setPortStart(rule.getPortStart());
        dto.setPortEnd(rule.getPortEnd());
        dto.setRemoteCidr(StringUtils.defaultIfBlank(rule.getRemoteCidr(), "0.0.0.0/0"));
        dto.setAction(StringUtils.defaultIfBlank(rule.getAction(), "accept"));
        dto.setPriority(rule.getPriority() == null ? 100 : rule.getPriority());
        return dto;
    }

    private List<String> getTargetIps(Vmhost vmhost) {
        if (NETWORK_TYPE_VPC.equalsIgnoreCase(vmhost.getNetworkType())) {
            return vmhost.getIpList() == null ? Collections.emptyList() : vmhost.getIpList();
        }
        return vmhost.getIpList() == null ? Collections.emptyList() : vmhost.getIpList();
    }

    private List<String> getSecurityGroupTargetIps(Integer groupId) {
        QueryWrapper<SecurityGroupBinding> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("group_id", groupId);
        queryWrapper.eq("status", STATUS_ACTIVE);
        List<String> ips = new ArrayList<>();
        for (SecurityGroupBinding binding : securityGroupBindingService.list(queryWrapper)) {
            Vmhost vmhost = getVmhostById(binding.getHostId());
            if (vmhost != null) {
                ips.addAll(getTargetIps(vmhost));
            }
        }
        return ips.stream().filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
    }

    private Set<Integer> getHostIdsByGroupId(Integer groupId) {
        QueryWrapper<SecurityGroupBinding> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("group_id", groupId);
        queryWrapper.eq("status", STATUS_ACTIVE);
        return securityGroupBindingService.list(queryWrapper).stream()
                .map(SecurityGroupBinding::getHostId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Integer> getHostIdsByReferencingGroupId(Integer groupId) {
        QueryWrapper<SecurityGroupRule> ruleQueryWrapper = new QueryWrapper<>();
        ruleQueryWrapper.eq("remote_group_id", groupId);
        ruleQueryWrapper.eq("status", STATUS_ACTIVE);
        Set<Integer> referencingGroupIds = securityGroupRuleService.list(ruleQueryWrapper).stream()
                .map(SecurityGroupRule::getGroupId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Integer> hostIds = new LinkedHashSet<>();
        for (Integer referencingGroupId : referencingGroupIds) {
            hostIds.addAll(getHostIdsByGroupId(referencingGroupId));
        }
        return hostIds;
    }

    private String resolveDefaultAction(List<Integer> groupIds, String direction) {
        for (Integer groupId : groupIds) {
            SecurityGroup group = securityGroupService.getById(groupId);
            if (group == null || !isGroupVisibleToHost(group, null)) {
                continue;
            }
            String action = "egress".equalsIgnoreCase(direction) ? group.getDefaultEgressAction() : group.getDefaultIngressAction();
            if (StringUtils.isNotBlank(action)) {
                return action;
            }
        }
        return "egress".equalsIgnoreCase(direction) ? "accept" : "drop";
    }

    private List<Integer> normalizeGroupIds(SecurityGroupBindParams params) {
        Set<Integer> groupIds = new LinkedHashSet<>();
        if (params.getGroupId() != null) {
            groupIds.add(params.getGroupId());
        }
        if (params.getGroupIds() != null) {
            groupIds.addAll(params.getGroupIds());
        }
        return new ArrayList<>(groupIds);
    }

    private SecurityGroupBinding getBinding(Integer hostId, Integer groupId) {
        QueryWrapper<SecurityGroupBinding> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("host_id", hostId);
        queryWrapper.eq("group_id", groupId);
        queryWrapper.last("limit 1");
        return securityGroupBindingService.getOne(queryWrapper);
    }

    private boolean isGroupVisibleToHost(SecurityGroup group, Integer hostId) {
        if (group == null) {
            return false;
        }
        return group.getHostId() == null || hostId == null || Objects.equals(group.getHostId(), hostId);
    }

    private Vmhost getVmhostById(Integer hostId) {
        return hostId == null ? null : vmhostDao.selectById(hostId);
    }

    private String buildRuleHash(SecurityGroupApplyDto applyDto) {
        return DigestUtils.sha256Hex(JSONObject.toJSONString(applyDto));
    }

    private void saveSyncState(Vmhost vmhost, String ruleHash, boolean inSync, String message) {
        QueryWrapper<SecurityGroupSync> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("host_id", vmhost.getId());
        queryWrapper.last("limit 1");
        SecurityGroupSync sync = securityGroupSyncService.getOne(queryWrapper);
        if (sync == null) {
            sync = new SecurityGroupSync();
            sync.setHostId(vmhost.getId());
            sync.setVmId(vmhost.getVmid());
            sync.setNodeId(vmhost.getNodeid());
        }
        sync.setRuleHash(ruleHash);
        sync.setInSync(inSync ? 1 : 0);
        sync.setLastMessage(message);
        sync.setLastSyncTime(System.currentTimeMillis());
        if (sync.getId() == null) {
            securityGroupSyncService.save(sync);
        } else {
            securityGroupSyncService.updateById(sync);
        }
    }
}

