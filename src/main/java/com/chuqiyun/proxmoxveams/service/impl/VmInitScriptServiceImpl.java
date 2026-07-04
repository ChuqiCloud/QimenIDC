package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.VmInitScriptDao;
import com.chuqiyun.proxmoxveams.entity.VmInitScript;
import com.chuqiyun.proxmoxveams.service.VmInitScriptService;
import org.springframework.stereotype.Service;

@Service("vmInitScriptService")
public class VmInitScriptServiceImpl extends ServiceImpl<VmInitScriptDao, VmInitScript> implements VmInitScriptService {
}
