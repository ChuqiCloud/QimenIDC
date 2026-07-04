package com.chuqiyun.proxmoxveams.controller.api.v1;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.annotation.PublicSysApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import com.chuqiyun.proxmoxveams.dto.SecurityGroupBindParams;
import com.chuqiyun.proxmoxveams.entity.SecurityGroup;
import com.chuqiyun.proxmoxveams.entity.SecurityGroupRule;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.SecurityGroupBusinessService;
import com.chuqiyun.proxmoxveams.service.SecurityGroupRuleService;
import com.chuqiyun.proxmoxveams.service.SecurityGroupService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @Author: 星禾
 * @Description: API虚拟机安全组管理
 * @DateTime: 2026/7/2 20:10
 */
@RestController
@RequestMapping("/api/v1")
public class SecurityGroupApi {
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_DELETED = 0;

    @Resource
    private SecurityGroupService securityGroupService;
    @Resource
    private SecurityGroupRuleService securityGroupRuleService;
    @Resource
    private SecurityGroupBusinessService securityGroupBusinessService;
    @Resource
    private VmhostService vmhostService;

    @PublicSysApiCheck
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/pve/securityGroup/add")
    public Object addSecurityGroup(@RequestBody SecurityGroup securityGroup) throws UnauthorizedException {
        if (securityGroup == null || securityGroup.getHostId() == null || !vmExists(securityGroup.getHostId())) {
            return ResponseResult.fail("虚拟机不存在");
        }
        long now = System.currentTimeMillis();
        securityGroup.setId(null);
        securityGroup.setDefaultIngressAction(defaultValue(securityGroup.getDefaultIngressAction(), "drop"));
        securityGroup.setDefaultEgressAction(defaultValue(securityGroup.getDefaultEgressAction(), "accept"));
        securityGroup.setIsTemplate(securityGroup.getIsTemplate() == null ? 0 : securityGroup.getIsTemplate());
        securityGroup.setIsDefault(securityGroup.getIsDefault() == null ? 0 : securityGroup.getIsDefault());
        securityGroup.setStatus(STATUS_ACTIVE);
        securityGroup.setCreateTime(now);
        securityGroup.setUpdateTime(now);
        if (!securityGroupService.save(securityGroup)) {
            return ResponseResult.fail("新增安全组失败");
        }
        if (!saveDefaultSecurityGroupRules(securityGroup.getId(), now)) {
            throw new IllegalStateException("新增安全组默认规则失败");
        }
        return ResponseResult.ok(securityGroup);
    }

    @PublicSysApiCheck
    @RequestMapping(value = "/pve/securityGroup/update", method = {RequestMethod.POST, RequestMethod.PUT})
    public Object updateSecurityGroup(@RequestBody SecurityGroup securityGroup) throws UnauthorizedException {
        if (securityGroup == null || securityGroup.getId() == null || securityGroup.getHostId() == null) {
            return ResponseResult.fail("参数不能为空");
        }
        SecurityGroup oldGroup = getHostSecurityGroup(securityGroup.getHostId(), securityGroup.getId());
        if (oldGroup == null) {
            return ResponseResult.fail("安全组不存在");
        }
        securityGroup.setUpdateTime(System.currentTimeMillis());
        if (StringUtils.isNotBlank(securityGroup.getDefaultIngressAction())) {
            securityGroup.setDefaultIngressAction(securityGroup.getDefaultIngressAction().trim().toLowerCase());
        }
        if (StringUtils.isNotBlank(securityGroup.getDefaultEgressAction())) {
            securityGroup.setDefaultEgressAction(securityGroup.getDefaultEgressAction().trim().toLowerCase());
        }
        securityGroup.setStatus(null);
        return securityGroupService.updateById(securityGroup) && securityGroupBusinessService.syncGroup(securityGroup.getId())
                ? ResponseResult.ok(securityGroup)
                : ResponseResult.fail("修改安全组失败");
    }

    @PublicSysApiCheck
    @RequestMapping(value = "/pve/securityGroup/delete", method = {RequestMethod.POST, RequestMethod.DELETE})
    public Object deleteSecurityGroup(@RequestBody SecurityGroup securityGroup) throws UnauthorizedException {
        if (securityGroup == null || securityGroup.getId() == null || securityGroup.getHostId() == null) {
            return ResponseResult.fail("参数不能为空");
        }
        SecurityGroup oldGroup = getHostSecurityGroup(securityGroup.getHostId(), securityGroup.getId());
        if (oldGroup == null) {
            return ResponseResult.fail("安全组不存在");
        }
        SecurityGroup update = new SecurityGroup();
        update.setId(oldGroup.getId());
        update.setStatus(STATUS_DELETED);
        update.setUpdateTime(System.currentTimeMillis());
        if (!securityGroupService.updateById(update)) {
            return ResponseResult.fail("删除安全组失败");
        }
        SecurityGroupBindParams params = new SecurityGroupBindParams();
        params.setHostId(oldGroup.getHostId());
        params.setGroupId(oldGroup.getId());
        securityGroupBusinessService.unbindVm(params);
        deleteRulesByGroupId(oldGroup.getId());
        securityGroupBusinessService.syncGroupsReferencing(Collections.singletonList(oldGroup.getId()));
        return ResponseResult.ok();
    }

    @PublicSysApiCheck
    @GetMapping("/pve/securityGroup/get")
    public Object getSecurityGroup(@RequestParam("hostId") Integer hostId,
                                   @RequestParam("id") Integer id) throws UnauthorizedException {
        SecurityGroup securityGroup = getHostSecurityGroup(hostId, id);
        fillApplyStatus(hostId, securityGroup);
        return securityGroup == null ? ResponseResult.fail("安全组不存在") : ResponseResult.ok(securityGroup);
    }

    @PublicSysApiCheck
    @GetMapping("/pve/securityGroup/page")
    public Object pageSecurityGroup(@RequestParam("hostId") Integer hostId,
                                    @RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "20") Integer size) throws UnauthorizedException {
        if (!vmExists(hostId)) {
            return ResponseResult.fail("虚拟机不存在");
        }
        QueryWrapper<SecurityGroup> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("host_id", hostId);
        queryWrapper.eq("status", STATUS_ACTIVE);
        queryWrapper.orderByDesc("id");
        Page<SecurityGroup> securityGroupPage = securityGroupService.page(new Page<>(page, size), queryWrapper);
        fillApplyStatus(hostId, securityGroupPage.getRecords());
        return ResponseResult.ok(securityGroupPage);
    }

    @PublicSysApiCheck
    @PostMapping("/pve/securityGroup/rule/add")
    public Object addRule(@RequestBody SecurityGroupRule rule) throws UnauthorizedException {
        if (rule == null || rule.getHostId() == null || rule.getGroupId() == null
                || getHostSecurityGroup(rule.getHostId(), rule.getGroupId()) == null) {
            return ResponseResult.fail("安全组不存在");
        }
        if (!isSameHostRemoteGroup(rule.getHostId(), rule.getRemoteGroupId())) {
            return ResponseResult.fail("引用安全组不属于当前虚拟机");
        }
        long now = System.currentTimeMillis();
        rule.setId(null);
        rule.setHostId(null);
        rule.setDirection(defaultValue(rule.getDirection(), "ingress"));
        rule.setProtocol(defaultValue(rule.getProtocol(), "all"));
        rule.setAction(defaultValue(rule.getAction(), "accept"));
        rule.setRemoteCidr(defaultValue(rule.getRemoteCidr(), "0.0.0.0/0"));
        rule.setPriority(rule.getPriority() == null ? 100 : rule.getPriority());
        rule.setStatus(STATUS_ACTIVE);
        rule.setCreateTime(now);
        rule.setUpdateTime(now);
        return securityGroupRuleService.save(rule) && securityGroupBusinessService.syncGroup(rule.getGroupId())
                ? ResponseResult.ok(rule)
                : ResponseResult.fail("新增安全组规则失败");
    }

    @PublicSysApiCheck
    @RequestMapping(value = "/pve/securityGroup/rule/update", method = {RequestMethod.POST, RequestMethod.PUT})
    public Object updateRule(@RequestBody SecurityGroupRule rule) throws UnauthorizedException {
        if (rule == null || rule.getHostId() == null || rule.getId() == null) {
            return ResponseResult.fail("参数不能为空");
        }
        SecurityGroupRule oldRule = getHostSecurityGroupRule(rule.getHostId(), rule.getId());
        if (oldRule == null) {
            return ResponseResult.fail("安全组规则不存在");
        }
        if (rule.getGroupId() != null && !Objects.equals(oldRule.getGroupId(), rule.getGroupId())) {
            return ResponseResult.fail("规则不允许变更所属安全组");
        }
        if (!isSameHostRemoteGroup(rule.getHostId(), rule.getRemoteGroupId())) {
            return ResponseResult.fail("引用安全组不属于当前虚拟机");
        }
        rule.setGroupId(oldRule.getGroupId());
        rule.setHostId(null);
        rule.setUpdateTime(System.currentTimeMillis());
        return securityGroupRuleService.updateById(rule) && securityGroupBusinessService.syncGroup(oldRule.getGroupId())
                ? ResponseResult.ok(rule)
                : ResponseResult.fail("修改安全组规则失败");
    }

    @PublicSysApiCheck
    @RequestMapping(value = "/pve/securityGroup/rule/delete", method = {RequestMethod.POST, RequestMethod.DELETE})
    public Object deleteRule(@RequestBody SecurityGroupRule rule) throws UnauthorizedException {
        if (rule == null || rule.getHostId() == null || rule.getId() == null) {
            return ResponseResult.fail("参数不能为空");
        }
        SecurityGroupRule oldRule = getHostSecurityGroupRule(rule.getHostId(), rule.getId());
        if (oldRule == null) {
            return ResponseResult.fail("安全组规则不存在");
        }
        SecurityGroupRule update = new SecurityGroupRule();
        update.setId(oldRule.getId());
        update.setStatus(STATUS_DELETED);
        update.setUpdateTime(System.currentTimeMillis());
        return securityGroupRuleService.updateById(update) && securityGroupBusinessService.syncGroup(oldRule.getGroupId())
                ? ResponseResult.ok()
                : ResponseResult.fail("删除安全组规则失败");
    }

    @PublicSysApiCheck
    @GetMapping("/pve/securityGroup/rule/list")
    public Object listRules(@RequestParam("hostId") Integer hostId,
                            @RequestParam("groupId") Integer groupId) throws UnauthorizedException {
        if (getHostSecurityGroup(hostId, groupId) == null) {
            return ResponseResult.fail("安全组不存在");
        }
        QueryWrapper<SecurityGroupRule> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("group_id", groupId);
        queryWrapper.eq("status", STATUS_ACTIVE);
        queryWrapper.orderByAsc("priority", "id");
        return ResponseResult.ok(securityGroupRuleService.list(queryWrapper));
    }

    @PublicSysApiCheck
    @RequestMapping(value = "/pve/securityGroup/apply", method = {RequestMethod.POST, RequestMethod.PUT})
    public Object applySecurityGroup(@RequestBody SecurityGroupBindParams params) throws UnauthorizedException {
        if (!checkBindParams(params)) {
            return ResponseResult.fail("安全组不属于当前虚拟机");
        }
        return securityGroupBusinessService.bindVm(params) ? ResponseResult.ok() : ResponseResult.fail("应用安全组失败");
    }

    @PublicSysApiCheck
    @RequestMapping(value = "/pve/securityGroup/unapply", method = {RequestMethod.POST, RequestMethod.DELETE})
    public Object unapplySecurityGroup(@RequestBody SecurityGroupBindParams params) throws UnauthorizedException {
        if (params == null || params.getHostId() == null || !vmExists(params.getHostId())) {
            return ResponseResult.fail("虚拟机不存在");
        }
        if (!checkUnapplyParams(params)) {
            return ResponseResult.fail("安全组不属于当前虚拟机");
        }
        return securityGroupBusinessService.unbindVm(params) ? ResponseResult.ok() : ResponseResult.fail("取消应用安全组失败");
    }

    @PublicSysApiCheck
    @RequestMapping(value = "/pve/securityGroup/sync", method = {RequestMethod.POST, RequestMethod.PUT})
    public Object syncVm(@RequestBody SecurityGroupBindParams params) throws UnauthorizedException {
        if (params == null || !vmExists(params.getHostId())) {
            return ResponseResult.fail("虚拟机不存在");
        }
        return securityGroupBusinessService.syncVm(params.getHostId()) ? ResponseResult.ok() : ResponseResult.fail("同步安全组失败");
    }

    @PublicSysApiCheck
    @GetMapping("/pve/securityGroup/check")
    public Object checkVm(@RequestParam("hostId") Integer hostId) throws UnauthorizedException {
        if (!vmExists(hostId)) {
            return ResponseResult.fail("虚拟机不存在");
        }
        return ResponseResult.ok(securityGroupBusinessService.checkVm(hostId));
    }

    private boolean checkBindParams(SecurityGroupBindParams params) {
        if (params == null || params.getHostId() == null || !vmExists(params.getHostId())) {
            return false;
        }
        if (params.getGroupId() != null && getHostSecurityGroup(params.getHostId(), params.getGroupId()) == null) {
            return false;
        }
        if (params.getGroupIds() != null) {
            for (Integer groupId : params.getGroupIds()) {
                if (getHostSecurityGroup(params.getHostId(), groupId) == null) {
                    return false;
                }
            }
        }
        return params.getGroupId() != null || params.getGroupIds() != null;
    }

    private boolean checkUnapplyParams(SecurityGroupBindParams params) {
        if (params == null || params.getHostId() == null || !vmExists(params.getHostId())) {
            return false;
        }
        if (params.getGroupId() == null && params.getGroupIds() == null) {
            return true;
        }
        return checkBindParams(params);
    }

    private SecurityGroup getHostSecurityGroup(Integer hostId, Integer groupId) {
        if (hostId == null || groupId == null) {
            return null;
        }
        QueryWrapper<SecurityGroup> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", groupId);
        queryWrapper.eq("host_id", hostId);
        queryWrapper.eq("status", STATUS_ACTIVE);
        queryWrapper.last("limit 1");
        return securityGroupService.getOne(queryWrapper);
    }

    private void fillApplyStatus(Integer hostId, SecurityGroup securityGroup) {
        if (securityGroup == null || securityGroup.getId() == null) {
            return;
        }
        List<Integer> boundGroupIds = securityGroupBusinessService.getBoundGroupIds(hostId);
        securityGroup.setApplyStatus(boundGroupIds.contains(securityGroup.getId()) ? 1 : 0);
    }

    private void fillApplyStatus(Integer hostId, List<SecurityGroup> securityGroups) {
        if (securityGroups == null || securityGroups.isEmpty()) {
            return;
        }
        List<Integer> boundGroupIds = securityGroupBusinessService.getBoundGroupIds(hostId);
        for (SecurityGroup securityGroup : securityGroups) {
            if (securityGroup != null && securityGroup.getId() != null) {
                securityGroup.setApplyStatus(boundGroupIds.contains(securityGroup.getId()) ? 1 : 0);
            }
        }
    }

    private SecurityGroupRule getHostSecurityGroupRule(Integer hostId, Integer ruleId) {
        if (hostId == null || ruleId == null) {
            return null;
        }
        SecurityGroupRule rule = securityGroupRuleService.getById(ruleId);
        if (rule == null || !Objects.equals(rule.getStatus(), STATUS_ACTIVE)) {
            return null;
        }
        return getHostSecurityGroup(hostId, rule.getGroupId()) == null ? null : rule;
    }

    private boolean isSameHostRemoteGroup(Integer hostId, Integer remoteGroupId) {
        return remoteGroupId == null || getHostSecurityGroup(hostId, remoteGroupId) != null;
    }

    private boolean vmExists(Integer hostId) {
        Vmhost vmhost = hostId == null ? null : vmhostService.getById(hostId);
        return vmhost != null;
    }

    private void deleteRulesByGroupId(Integer groupId) {
        UpdateWrapper<SecurityGroupRule> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("group_id", groupId);
        updateWrapper.set("status", STATUS_DELETED);
        updateWrapper.set("update_time", System.currentTimeMillis());
        securityGroupRuleService.update(updateWrapper);
    }

    private boolean saveDefaultSecurityGroupRules(Integer groupId, long now) {
        if (groupId == null) {
            return false;
        }
        List<SecurityGroupRule> rules = new ArrayList<>();
        rules.add(buildDefaultRule(groupId, "tcp", 22, 22, 10, "默认允许SSH"));
        rules.add(buildDefaultRule(groupId, "tcp", 3389, 3389, 20, "默认允许RDP"));
        rules.add(buildDefaultRule(groupId, "tcp", 80, 80, 30, "默认允许HTTP"));
        rules.add(buildDefaultRule(groupId, "tcp", 443, 443, 40, "默认允许HTTPS"));
        rules.add(buildDefaultRule(groupId, "icmp", null, null, 50, "默认允许ICMP"));
        for (SecurityGroupRule rule : rules) {
            rule.setCreateTime(now);
            rule.setUpdateTime(now);
        }
        return securityGroupRuleService.saveBatch(rules);
    }

    private SecurityGroupRule buildDefaultRule(Integer groupId, String protocol, Integer portStart,
                                               Integer portEnd, Integer priority, String remark) {
        SecurityGroupRule rule = new SecurityGroupRule();
        rule.setGroupId(groupId);
        rule.setDirection("ingress");
        rule.setProtocol(protocol);
        rule.setPortStart(portStart);
        rule.setPortEnd(portEnd);
        rule.setRemoteCidr("0.0.0.0/0");
        rule.setAction("accept");
        rule.setPriority(priority);
        rule.setRemark(remark);
        rule.setStatus(STATUS_ACTIVE);
        return rule;
    }

    private String defaultValue(String value, String defaultValue) {
        return StringUtils.isBlank(value) ? defaultValue : value.trim().toLowerCase();
    }
}
