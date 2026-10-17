package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.SysuserDao;
import com.chuqiyun.proxmoxveams.entity.Sysuser;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.utils.EncryptUtil;
import com.chuqiyun.proxmoxveams.utils.ModUtil;
import com.chuqiyun.proxmoxveams.utils.UUIDUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * (Sysuser)表服务实现类
 *
 * @author mryunqi
 * @since 2023-06-10 15:31:52
 */
@Service("sysuserService")
public class SysuserServiceImpl extends ServiceImpl<SysuserDao, Sysuser> implements SysuserService {
    /**
     * @Author: mryunqi
     * @Description: 获取单个系统管理员用户数据
     * @DateTime: 2023/4/14 22:22
     * @Params: String uuid
     * @Return Sysuser
     */
    @Override
    public Sysuser getSysuserByUuid(String uuid) {
        QueryWrapper<Sysuser> sysuserQueryWrapper = new QueryWrapper<>();
        sysuserQueryWrapper.eq("uuid", uuid);
        return this.getOne(sysuserQueryWrapper);
    }

    /**
    * @Author: mryunqi
    * @Description: 根据手机号获取用户数据
    * @DateTime: 2023/8/5 14:56
    * @Params: String phone 手机号
    * @Return  Sysuser 返回用户数据
    */
    @Override
    public Sysuser getSysuserByPhone(String phone) {
        QueryWrapper<Sysuser> sysuserQueryWrapper = new QueryWrapper<>();
        sysuserQueryWrapper.eq("phone", phone);
        return this.getOne(sysuserQueryWrapper);
    }

    /**
    * @Author: mryunqi
    * @Description: 根据用户名获取用户数据
    * @DateTime: 2023/8/5 14:57
    * @Params: String username 用户名
    * @Return Sysuser 返回用户数据
    */
    @Override
    public Sysuser getSysuserByUsername(String username) {
        QueryWrapper<Sysuser> sysuserQueryWrapper = new QueryWrapper<>();
        sysuserQueryWrapper.eq("username", username);
        return this.getOne(sysuserQueryWrapper);
    }

    /**
     * @Author: mryunqi
     * @Description: 插入新用户账号数据，同等于save，可以不用该行为方法
     * @DateTime: 2023/4/16 11:22
     * @Params: Sysuser sysuser
     * @Return int
     */
    @Override
    public boolean insertSysuser(Sysuser sysuser) {
        //return this.baseMapper.insertSysuser(sysuser);
        return this.save(sysuser);
    }

    /**
    * @Author: mryunqi
    * @Description: 创建初始管理员账号
    * @DateTime: 2023/8/4 23:29
    * @Return Sysuser 返回账号密码
    */
    @Override
    public Sysuser insertInitSysuser() {
        Sysuser user = new Sysuser();
        String uuid = UUIDUtil.getUUIDByThreadString();
        String randomPassword = ModUtil.randomPassword();
        user.setUuid(uuid);
        // 设置账号为admin
        user.setUsername("admin");
        // 密码随机生成
        user.setPassword(EncryptUtil.md5(randomPassword));
        // 存入数据库
        this.save(user);
        // 将密码覆盖
        user.setPassword(randomPassword);
        // 返回账号密码
        return user;
    }

    /**
    * @Author: mryunqi
    * @Description: 查询超管账号列表，分页
    * @DateTime: 2023/8/5 9:29
    * @Params: Integer page 页码 Integer limit 每页数量
    * @Return  Page<Sysuser> 返回分页数据
    */
    @Override
    public Page<Sysuser> selectUserPage(Integer page, Integer limit) {
        Page<Sysuser> sysuserPage = new Page<>(page, limit);
        return this.page(sysuserPage);
    }

    /**
    * @Author: mryunqi
    * @Description: 删除超管账号
    * @DateTime: 2024/2/17 17:10
    * @Params: Integer id 账号id
    * @Return boolean 返回删除结果
    */
    @Override
    public boolean deleteSysuserById(Integer id) {
        return this.removeById(id);
    }

    /**
    * @Author: 星禾
    * @Description: 管理员重置自己的密码，原密码校验通过后写入新密码
    * @DateTime: 2026/10/17 15:30
    * @Params: Integer userId 管理员ID String oldPassword 原密码 String newPassword 新密码
    * @Return String 失败原因，成功返回 null
    */
    @Override
    public String resetOwnPassword(Integer userId, String oldPassword, String newPassword) {
        if (StringUtils.isAnyBlank(oldPassword, newPassword)) {
            return "原密码和新密码不能为空";
        }
        if (newPassword.length() < 8 || newPassword.length() > 64) {
            return "新密码长度需为 8-64 位";
        }
        if (oldPassword.equals(newPassword)) {
            return "新密码不能与原密码相同";
        }
        Sysuser sysuser = this.getById(userId);
        if (Objects.isNull(sysuser)) {
            return "账号不存在";
        }
        if (!EncryptUtil.verifyPassword(oldPassword, sysuser.getPassword())) {
            return "原密码错误";
        }
        // 与会话轮换使用同一比较写入机制，避免同毫秒改密保留旧 Token。
        for (int attempt = 0; attempt < 10; attempt++) {
            Long previous = sysuser.getLogindate();
            long next = nextLoginDate(previous);
            UpdateWrapper<Sysuser> wrapper = sessionUpdate(userId, previous);
            wrapper.eq("password", sysuser.getPassword())
                    .set("password", EncryptUtil.md5(newPassword))
                    .set("logindate", next);
            if (this.update(wrapper)) {
                return null;
            }
            sysuser = this.getById(userId);
            if (sysuser == null || !EncryptUtil.verifyPassword(oldPassword, sysuser.getPassword())) {
                return "原密码已变更，请重新登录";
            }
        }
        return "密码修改失败，请重试";
    }

    @Override
    public boolean updateAccountDetails(Sysuser updates, String encodedPassword) {
        if (updates == null || updates.getId() == null) {
            return false;
        }
        boolean rotateSession = StringUtils.isNotBlank(encodedPassword);
        for (int attempt = 0; attempt < (rotateSession ? 10 : 1); attempt++) {
            Sysuser current = rotateSession ? this.getById(updates.getId()) : null;
            if (rotateSession && current == null) {
                return false;
            }

            UpdateWrapper<Sysuser> wrapper = new UpdateWrapper<>();
            wrapper.eq("id", updates.getId());
            if (rotateSession) {
                wrapper = sessionUpdate(updates.getId(), current.getLogindate());
            }
            boolean hasUpdate = setIfPresent(wrapper, "username", updates.getUsername());
            hasUpdate |= setIfPresent(wrapper, "name", updates.getName());
            hasUpdate |= setIfPresent(wrapper, "phone", updates.getPhone());
            hasUpdate |= setIfPresent(wrapper, "email", updates.getEmail());
            if (rotateSession) {
                wrapper.set("password", encodedPassword)
                        .set("logindate", nextLoginDate(current.getLogindate()));
                hasUpdate = true;
            }
            if (!hasUpdate) {
                return true;
            }
            if (this.update(wrapper)) {
                return true;
            }
            if (!rotateSession) {
                return false;
            }
        }
        return false;
    }

    private boolean setIfPresent(UpdateWrapper<Sysuser> wrapper, String column, String value) {
        if (value == null) {
            return false;
        }
        wrapper.set(column, value);
        return true;
    }

    /**
    * @Author: 星禾
    * @Description: 校验指定账号的密码
    * @DateTime: 2026/10/17 17:10
    * @Params: Integer userId 管理员ID String password 明文密码
    * @Return boolean 是否匹配
    */
    @Override
    public boolean verifyPassword(Integer userId, String password) {
        if (Objects.isNull(userId) || StringUtils.isBlank(password)) {
            return false;
        }
        Sysuser sysuser = this.getById(userId);
        return !Objects.isNull(sysuser) && EncryptUtil.verifyPassword(password, sysuser.getPassword());
    }

    /**
    * @Author: 星禾
    * @Description: 更新二步验证相关字段，避免整行覆盖写回旧值
    * @DateTime: 2026/10/17 15:30
    * @Params: Sysuser sysuser 需要更新的字段
    * @Return boolean 是否更新成功
    */
    @Override
    public boolean updateMfaSettings(Sysuser sysuser) {
        UpdateWrapper<Sysuser> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", sysuser.getId())
                .set("mfa_type", sysuser.getMfaType())
                .set("passkey_enabled", Objects.isNull(sysuser.getPasskeyEnabled()) ? 0 : sysuser.getPasskeyEnabled());
        return this.update(wrapper);
    }

    @Override
    public boolean startTotpBinding(Integer userId, String encryptedSecret) {
        UpdateWrapper<Sysuser> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", userId).isNull("totp_confirmed_time")
                .set("totp_secret", encryptedSecret).set("totp_last_step", null);
        return this.update(wrapper);
    }

    @Override
    public boolean confirmTotpBinding(Integer userId, String encryptedSecret, long step) {
        UpdateWrapper<Sysuser> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", userId).eq("totp_secret", encryptedSecret).isNull("totp_confirmed_time")
                .and(w -> w.isNull("totp_last_step").or().lt("totp_last_step", step))
                .set("totp_confirmed_time", System.currentTimeMillis()).set("totp_last_step", step);
        return this.update(wrapper);
    }

    @Override
    public boolean clearTotpBinding(Integer userId) {
        UpdateWrapper<Sysuser> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", userId).set("totp_secret", null).set("totp_confirmed_time", null)
                .set("totp_last_step", null)
                .setSql("mfa_type = case when mfa_type = 'totp' then 'none' else mfa_type end");
        return this.update(wrapper);
    }

    @Override
    public Long rotateLoginSession(Integer userId) {
        for (int attempt = 0; attempt < 10; attempt++) {
            Sysuser user = this.getById(userId);
            if (user == null) {
                return null;
            }
            long next = nextLoginDate(user.getLogindate());
            UpdateWrapper<Sysuser> wrapper = sessionUpdate(userId, user.getLogindate());
            wrapper.set("logindate", next);
            if (this.update(wrapper)) {
                return next;
            }
        }
        return null;
    }

    private UpdateWrapper<Sysuser> sessionUpdate(Integer userId, Long previous) {
        UpdateWrapper<Sysuser> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", userId);
        if (previous == null) {
            wrapper.isNull("logindate");
        } else {
            wrapper.eq("logindate", previous);
        }
        return wrapper;
    }

    private long nextLoginDate(Long previous) {
        return Math.max(System.currentTimeMillis(), previous == null ? 0L : Math.addExact(previous, 1L));
    }

    /**
    * @Author: 星禾
    * @Description: TOTP 时间片重放防护，只有比已消费时间片更新时才写入
    * @DateTime: 2026/10/17 15:30
    * @Params: Integer userId 管理员ID long step 命中的时间片
    * @Return boolean true 表示消费成功，false 表示该时间片已被使用
    */
    @Override
    public boolean consumeTotpStep(Integer userId, long step) {
        UpdateWrapper<Sysuser> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", userId)
                .and(wrapper1 -> wrapper1.isNull("totp_last_step").or().lt("totp_last_step", step))
                .set("totp_last_step", step);
        return this.update(wrapper);
    }

}

