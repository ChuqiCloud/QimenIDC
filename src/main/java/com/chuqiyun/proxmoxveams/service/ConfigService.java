package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.Config;

/**
 * (Config)表服务接口
 *
 * @author mryunqi
 * @since 2023-07-11 17:58:04
 */
public interface ConfigService extends IService<Config> {

    String getToken();

    Integer getLinuxSystemDiskSize();

    Integer getWinSystemDiskSize();

    Long getBwlimit();

    Integer getVncExpire();

    String getVersion();

    String getBuild();

    Boolean getInstalled();

    Boolean setInstalled(Boolean installed);

    Boolean setBuild(String build);

    Boolean initConfig();

    Config getConfig();

    Boolean updateConfig(Config config);
}

