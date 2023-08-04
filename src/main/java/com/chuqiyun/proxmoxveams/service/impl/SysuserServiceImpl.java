package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.SysuserDao;
import com.chuqiyun.proxmoxveams.entity.Sysuser;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.utils.EncryptUtil;
import com.chuqiyun.proxmoxveams.utils.ModUtil;
import org.springframework.stereotype.Service;

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
     * @Params: String phone
     * @Return Sysuser
     */
    @Override
    public Sysuser getSysuser(String phone) {
        QueryWrapper<Sysuser> sysuserQueryWrapper = new QueryWrapper<>();
        sysuserQueryWrapper.eq("phone", phone);
        // 判断是否存在该用户
        if (this.count(sysuserQueryWrapper) == 0) {
            // 将条件phone改为username
            sysuserQueryWrapper.clear();
            sysuserQueryWrapper.eq("username", phone);
        }
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
    public int insertSysuser(Sysuser sysuser) {
        return this.baseMapper.insertSysuser(sysuser);
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
        String randomPassword = ModUtil.randomPassword();
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

}

