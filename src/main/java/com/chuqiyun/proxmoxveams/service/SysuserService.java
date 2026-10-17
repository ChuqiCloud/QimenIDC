package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.Sysuser;

/**
 * (Sysuser)表服务接口
 *
 * @author mryunqi
 * @since 2023-06-10 15:31:52
 */
public interface SysuserService extends IService<Sysuser> {

    Sysuser getSysuserByUuid(String uuid);

    Sysuser getSysuserByPhone(String phone);

    Sysuser getSysuserByUsername(String username);

    boolean insertSysuser(Sysuser sysuser);

    Sysuser insertInitSysuser();

    Page<Sysuser> selectUserPage(Integer page, Integer limit);

    boolean deleteSysuserById(Integer id);

    /**
     * 管理员重置自己的密码，并吊销其它会话
     *
     * @param userId      管理员ID
     * @param oldPassword 原密码
     * @param newPassword 新密码
     * @return 重置结果说明，成功返回 null
     */
    String resetOwnPassword(Integer userId, String oldPassword, String newPassword);

    /**
     * 校验指定账号的密码
     */
    boolean verifyPassword(Integer userId, String password);

    /**
     * 更新二步验证配置
     */
    boolean updateMfaSettings(Sysuser sysuser);

    /**
     * 消费一个 TOTP 时间片，返回 false 表示该时间片已被使用（重放）
     */
    boolean consumeTotpStep(Integer userId, long step);
}

