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
        // 更新登录时间即让已签发的 token 失效，强制其它会话重新登录
        UpdateWrapper<Sysuser> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", userId)
                .set("password", EncryptUtil.md5(newPassword))
                .set("logindate", System.currentTimeMillis());
        return this.update(wrapper) ? null : "密码修改失败";
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
                .set("totp_secret", sysuser.getTotpSecret())
                .set("totp_confirmed_time", sysuser.getTotpConfirmedTime())
                .set("totp_last_step", sysuser.getTotpLastStep())
                .set("passkey_enabled", Objects.isNull(sysuser.getPasskeyEnabled()) ? 0 : sysuser.getPasskeyEnabled());
        return this.update(wrapper);
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

