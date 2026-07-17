package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.NatSyncStateDao;
import com.chuqiyun.proxmoxveams.entity.NatSyncState;
import com.chuqiyun.proxmoxveams.service.NatSyncStateService;
import org.springframework.stereotype.Service;

@Service("natSyncStateService")
public class NatSyncStateServiceImpl extends ServiceImpl<NatSyncStateDao, NatSyncState> implements NatSyncStateService {
}
