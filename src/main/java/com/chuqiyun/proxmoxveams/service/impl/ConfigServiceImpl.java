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

    /**
    * @Author: mryunqi
    * @Description: 获取正式版号
    * @DateTime: 2023/11/28 18:23
    * @Return String version
    */
    @Override
    public String getVersion(){
        return this.getById(1).getVersion();
    }

    /**
    * @Author: mryunqi
    * @Description: 获去内部构建号
    * @DateTime: 2023/11/28 18:24
    * @Return String build
    */
    @Override
    public String getBuild(){
        // return this.getById(1).getBuild();
        try {
            return this.getById(1).getBuild();
        } catch (Exception e) {
            return null; // 如果出现异常，返回null
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 获取是否已导入数据库
    * @DateTime: 2023/12/3 16:37
    * @Return Boolean true:已导入  false:未导入
    */
    @Override
    public Boolean getInstalled(){
        // return this.getById(1).getInstalled() == 1;
        try {
            return this.getById(1).getInstalled() == 1;
        } catch (Exception e) {
            e.printStackTrace();// 打印异常信息
            return false; // 如果出现异常，返回false
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 设置是否已导入数据库
    * @DateTime: 2023/12/3 16:38
    * @Params: Boolean installed true:已导入  false:未导入
    * @Return Boolean true:成功  false:失败
    */
    @Override
    public Boolean setInstalled(Boolean installed){
        Config config = this.getById(1);
        config.setInstalled(installed ? 1 : 0);
        return this.updateById(config);
    }

    /**
    * @Author: mryunqi
    * @Description: 设置内部构件号
    * @DateTime: 2023/12/3 17:03
    * @Params: String build
    * @Return Boolean true:成功  false:失败
    */
    @Override
    public Boolean setBuild(String build){
        Config config = this.getById(1);
        config.setBuild(build);
        return this.updateById(config);
    }

    /**
    * @Author: mryunqi
    * @Description: 初始化配置表
    * @DateTime: 2024/1/15 22:27
    * @Return Boolean true:成功  false:失败
    */
    @Override
    public Boolean initConfig(){
        Config config = new Config();
        config.setId(1);
        config.setInstalled(1);
        return this.save(config);
    }

}

