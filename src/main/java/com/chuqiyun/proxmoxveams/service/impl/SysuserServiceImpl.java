package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.SysuserDao;
import com.chuqiyun.proxmoxveams.entity.Sysuser;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.utils.EncryptUtil;
import com.chuqiyun.proxmoxveams.utils.ModUtil;
import com.chuqiyun.proxmoxveams.utils.UUIDUtil;
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

}

