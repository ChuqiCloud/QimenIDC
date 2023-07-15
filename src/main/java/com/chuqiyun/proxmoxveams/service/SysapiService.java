package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.Sysapi;

/**
 * (Sysapi)表服务接口
 *
 * @author mryunqi
 * @since 2023-06-10 19:11:15
 */
public interface SysapiService extends IService<Sysapi> {

    Sysapi getSysapi(String appId);
}

