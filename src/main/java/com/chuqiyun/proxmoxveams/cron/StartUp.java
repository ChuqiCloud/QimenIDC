package com.chuqiyun.proxmoxveams.cron;

import com.chuqiyun.proxmoxveams.entity.Config;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.utils.UUIDUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Objects;

/**
 * @author mryunqi
 * @date 2023/6/18
 */
@Slf4j
@Component
public class StartUp {
    @Resource
    private ConfigService configService;
    @Resource
    private MasterService masterService;
    @PostConstruct
    public void init() {
        // 初始化受控端token
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
        log.info("[System] 集群预热开始");
        // 预热所有节点cookie
        masterService.updateAllNodeCookie();
        log.info("[System] 集群预热结束");
    }
}
