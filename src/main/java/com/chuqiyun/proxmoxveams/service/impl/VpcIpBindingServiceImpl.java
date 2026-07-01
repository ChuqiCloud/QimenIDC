package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.VpcIpBindingDao;
import com.chuqiyun.proxmoxveams.entity.VpcIpBinding;
import com.chuqiyun.proxmoxveams.service.VpcIpBindingService;
import org.springframework.stereotype.Service;

/**
 * @Author: 星禾
 * @Description: VPC IP绑定服务实现
 * @DateTime: 2026/7/1 21:00
 */
@Service("vpcIpBindingService")
public class VpcIpBindingServiceImpl extends ServiceImpl<VpcIpBindingDao, VpcIpBinding> implements VpcIpBindingService {
}
