package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.SecurityGroupRuleDao;
import com.chuqiyun.proxmoxveams.entity.SecurityGroupRule;
import com.chuqiyun.proxmoxveams.service.SecurityGroupRuleService;
import org.springframework.stereotype.Service;

@Service("securityGroupRuleService")
public class SecurityGroupRuleServiceImpl extends ServiceImpl<SecurityGroupRuleDao, SecurityGroupRule> implements SecurityGroupRuleService {
}
