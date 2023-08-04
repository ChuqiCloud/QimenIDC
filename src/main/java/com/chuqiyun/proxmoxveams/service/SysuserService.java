package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.Sysuser;

/**
 * (Sysuser)表服务接口
 *
 * @author mryunqi
 * @since 2023-06-10 15:31:52
 */
public interface SysuserService extends IService<Sysuser> {

    Sysuser getSysuser(String phone);

    int insertSysuser(Sysuser sysuser);

    Sysuser insertInitSysuser();
}

