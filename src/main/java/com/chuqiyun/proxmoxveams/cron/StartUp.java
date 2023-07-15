package com.chuqiyun.proxmoxveams.cron;

import com.chuqiyun.proxmoxveams.entity.Config;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import com.chuqiyun.proxmoxveams.utils.UUIDUtil;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Objects;

/**
 * @author mryunqi
 * @date 2023/6/18
 */
@Component
public class StartUp {
    @Resource
    private ConfigService configService;
    @PostConstruct
    public void init() {
        Config config = configService.getById(1);
        String token = UUIDUtil.getUUIDByThread();
        if (config == null){
            Config newConfig = new Config();
            newConfig.setId(1);
            newConfig.setToken(token);
            configService.save(newConfig);
        }
        // 刷新数据
        config = configService.getById(1);
        assert config != null;
        if (Objects.equals(config.getToken(), "0")){
            config.setToken(token);
            configService.updateById(config);
        }

    }
}
