package com.chuqiyun.proxmoxveams.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chuqiyun.proxmoxveams.entity.Sysuser;

/**
 * (Sysuser)表数据库访问层
 *
 * @author mryunqi
 * @since 2023-06-10 15:31:52
 */
public interface SysuserDao extends BaseMapper<Sysuser> {

    /**
     * @Author: mryunqi
     * @Description: 插入新管理员账号
     * @DateTime: 2023/4/16 11:19
     * @Params: Sysuser 实体
     * @Return int 0失败1成功
     */
    int insertSysuser(Sysuser sysuser);

}

