package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.NatForwardRuleDao;
import com.chuqiyun.proxmoxveams.entity.NatForwardRule;
import com.chuqiyun.proxmoxveams.service.NatForwardRuleService;
import org.springframework.stereotype.Service;

@Service("natForwardRuleService")
public class NatForwardRuleServiceImpl extends ServiceImpl<NatForwardRuleDao, NatForwardRule> implements NatForwardRuleService {
}
