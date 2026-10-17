package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.SysPasskeyDao;
import com.chuqiyun.proxmoxveams.entity.SysPasskey;
import com.chuqiyun.proxmoxveams.service.SysPasskeyService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * (SysPasskey)表服务实现类
 *
 * @author 星禾
 * @since 2026-10-17
 */
@Service("sysPasskeyService")
public class SysPasskeyServiceImpl extends ServiceImpl<SysPasskeyDao, SysPasskey> implements SysPasskeyService {

    @Override
    public List<SysPasskey> listByUserId(Integer userId) {
        QueryWrapper<SysPasskey> wrapper = new QueryWrapper<>();
        wrapper.eq("userid", userId).orderByDesc("create_time");
        return this.list(wrapper);
    }

    @Override
    public SysPasskey getByCredentialId(String credentialId) {
        QueryWrapper<SysPasskey> wrapper = new QueryWrapper<>();
        wrapper.eq("credential_id", credentialId);
        return this.getOne(wrapper);
    }

    @Override
    public int countByUserId(Integer userId) {
        QueryWrapper<SysPasskey> wrapper = new QueryWrapper<>();
        wrapper.eq("userid", userId);
        return Math.toIntExact(this.count(wrapper));
    }

    @Override
    public boolean removePasskey(Long id, Integer userId) {
        QueryWrapper<SysPasskey> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id).eq("userid", userId);
        return this.remove(wrapper);
    }

    /**
     * 只更新计数器与使用时间，避免并发校验时互相覆盖整行数据
     */
    @Override
    public void touchLastUsed(SysPasskey passkey, long signCount, long lastUsedTime) {
        UpdateWrapper<SysPasskey> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", passkey.getId())
                .set("sign_count", signCount)
                .set("last_used_time", lastUsedTime);
        this.update(wrapper);
    }
}
