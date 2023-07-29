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

}

