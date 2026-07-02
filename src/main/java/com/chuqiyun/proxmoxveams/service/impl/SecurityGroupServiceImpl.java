package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.SecurityGroupDao;
import com.chuqiyun.proxmoxveams.entity.SecurityGroup;
import com.chuqiyun.proxmoxveams.service.SecurityGroupService;
import org.springframework.stereotype.Service;

@Service("securityGroupService")
public class SecurityGroupServiceImpl extends ServiceImpl<SecurityGroupDao, SecurityGroup> implements SecurityGroupService {
}
