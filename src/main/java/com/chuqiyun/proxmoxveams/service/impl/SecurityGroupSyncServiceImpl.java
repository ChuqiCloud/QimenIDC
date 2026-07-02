package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.SecurityGroupSyncDao;
import com.chuqiyun.proxmoxveams.entity.SecurityGroupSync;
import com.chuqiyun.proxmoxveams.service.SecurityGroupSyncService;
import org.springframework.stereotype.Service;

@Service("securityGroupSyncService")
public class SecurityGroupSyncServiceImpl extends ServiceImpl<SecurityGroupSyncDao, SecurityGroupSync> implements SecurityGroupSyncService {
}
