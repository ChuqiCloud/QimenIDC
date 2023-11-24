package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.ConfigDao;
import com.chuqiyun.proxmoxveams.entity.Config;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import org.springframework.stereotype.Service;

/**
 * (Config)表服务实现类
 *
 * @author mryunqi
 * @since 2023-07-11 17:58:04
 */
@Service("configService")
public class ConfigServiceImpl extends ServiceImpl<ConfigDao, Config> implements ConfigService {
    /**
    * @Author: mryunqi
    * @Description: 获取受控端token
    * @DateTime: 2023/7/29 22:30
    * @Return String token
    */
    @Override
    public String getToken(){
        return this.getById(1).getToken();
    }

    /**
    * @Author: mryunqi
    * @Description: 获取全局Linux系统盘大小
    * @DateTime: 2023/8/7 19:47
    * @Return Integer linuxSystemDiskSize
    */
    @Override
    public Integer getLinuxSystemDiskSize(){
        return this.getById(1).getLinuxSystemDiskSize();
    }

    /**
    * @Author: mryunqi
    * @Description: 获取全局Windows系统盘大小
    * @DateTime: 2023/8/7 19:48
    * @Return Integer winSystemDiskSize
    */
    @Override
    public Integer getWinSystemDiskSize(){
        return this.getById(1).getWinSystemDiskSize();
    }

    /**
    * @Author: mryunqi
    * @Description: 获取全局默认bwlimit限制
    * @DateTime: 2023/9/25 17:01
    * @Return Long bwlimit
    */
    @Override
    public Long getBwlimit(){
        return this.getById(1).getBwlimit();
    }

    /**
    * @Author: mryunqi
    * @Description: 获取全局默认VNC有效期
    * @DateTime: 2023/11/24 19:01
    * @Return Integer vncTime
    */
    @Override
    public Integer getVncExpire(){
        return this.getById(1).getVncTime();
    }
}

