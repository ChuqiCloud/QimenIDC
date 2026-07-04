package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.VmInitScriptRecordDao;
import com.chuqiyun.proxmoxveams.entity.VmInitScriptRecord;
import com.chuqiyun.proxmoxveams.service.VmInitScriptRecordService;
import org.springframework.stereotype.Service;

@Service("vmInitScriptRecordService")
public class VmInitScriptRecordServiceImpl extends ServiceImpl<VmInitScriptRecordDao, VmInitScriptRecord> implements VmInitScriptRecordService {
}
