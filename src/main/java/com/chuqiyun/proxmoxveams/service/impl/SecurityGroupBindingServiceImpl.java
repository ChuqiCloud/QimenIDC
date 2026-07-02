package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.SecurityGroupBindingDao;
import com.chuqiyun.proxmoxveams.entity.SecurityGroupBinding;
import com.chuqiyun.proxmoxveams.service.SecurityGroupBindingService;
import org.springframework.stereotype.Service;

@Service("securityGroupBindingService")
public class SecurityGroupBindingServiceImpl extends ServiceImpl<SecurityGroupBindingDao, SecurityGroupBinding> implements SecurityGroupBindingService {
}
