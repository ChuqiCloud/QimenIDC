package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.SysPasskey;

import java.util.List;

/**
 * (SysPasskey)表服务接口
 *
 * @author 星禾
 * @since 2026-10-17
 */
public interface SysPasskeyService extends IService<SysPasskey> {

    List<SysPasskey> listByUserId(Integer userId);

    SysPasskey getByCredentialId(String credentialId);

    int countByUserId(Integer userId);

    /**
     * 解绑指定账号下的 Passkey
     */
    boolean removePasskey(Long id, Integer userId);

    void touchLastUsed(SysPasskey passkey, long signCount, long lastUsedTime);
}
