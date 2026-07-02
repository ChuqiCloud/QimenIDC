package com.chuqiyun.proxmoxveams.service;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.dto.SecurityGroupBindParams;

import java.util.List;

public interface SecurityGroupBusinessService {
    Boolean bindVm(SecurityGroupBindParams params);

    Boolean unbindVm(SecurityGroupBindParams params);

    Boolean syncVm(Integer hostId);

    Boolean syncGroup(Integer groupId);

    Boolean syncGroupsReferencing(List<Integer> groupIds);

    Boolean deleteVmRules(Integer hostId);

    Boolean deleteVmSecurityGroups(Integer hostId);

    JSONObject checkVm(Integer hostId);

    List<Integer> getBoundGroupIds(Integer hostId);
}
