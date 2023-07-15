package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.SysuserDao;
import com.chuqiyun.proxmoxveams.entity.Sysuser;
import com.chuqiyun.proxmoxveams.service.SysuserService;
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
}

