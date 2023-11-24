package com.chuqiyun.proxmoxveams.cron;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Vncdata;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.VncdataService;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author mryunqi
 * @date 2023/11/24
 */
@Component
@EnableScheduling
public class VncConnectCron {
    @Resource
    private ConfigService configService;
    @Resource
    private VncdataService vncdataService;
    @Resource
    private MasterService masterService;

    /**
    * @Author: mryunqi
    * @Description: vnc连接失效定时任务
    * @DateTime: 2023/11/24 22:38
    */
    @Async
    @Scheduled(fixedDelay = 1000)
    public void vncConnectCron() {
        // 分页获取VNC连接信息，状态为0的
        QueryWrapper<Vncdata> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", 0);
        String token = configService.getToken();
        int page = 1;
        int limit = 100;
        while (true) {
            Page<Vncdata> vncdataPage = vncdataService.selectVncdataPage(page, limit, queryWrapper);
            if (vncdataPage == null || vncdataPage.getRecords().size() == 0) {
                break;
            }
            for (Vncdata vncdata : vncdataPage.getRecords()) {
                // 当前时间戳
                long currentTime = System.currentTimeMillis();
                // VNC连接失效时间戳
                long vncExpiryTimestamp = vncdata.getExpirationTime();
                // 当前时间戳大于VNC连接失效时间戳，说明VNC连接已失效
                if (currentTime >= vncExpiryTimestamp) {
                    Master node = masterService.getById(vncdata.getNodeId());
                    if (node == null || node.getStatus() != 0) {
                        // 直接停止VNC连接
                        vncdata.setStatus(1);
                        vncdataService.updateVncdata(vncdata);
                        continue;
                    }
                    // 停止VNC连接
                    try {
                        ClientApiUtil.stopVncService(node.getHost(),token,vncdata.getPort(), node.getControllerPort());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    // 更新VNC连接状态
                    vncdata.setStatus(1);
                    vncdataService.updateVncdata(vncdata);
                }
            }
            page++;
        }

    }
}
