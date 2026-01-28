package com.chuqiyun.proxmoxveams.cron;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chuqiyun.proxmoxveams.entity.Ippool;
import com.chuqiyun.proxmoxveams.entity.Ipstatus;
import com.chuqiyun.proxmoxveams.service.IppoolService;
import com.chuqiyun.proxmoxveams.service.IpstatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author mryunqi
 * @date 2023/7/4
 */
@Slf4j
@Component
@EnableScheduling
public class IpStatusCron {
    @Resource
    private IpstatusService ipstatusService;
    @Resource
    private IppoolService ippoolService;
    @Async
    @Scheduled(fixedDelay = 2000)
    public void ipStatusCron(){
        // 获取IP池所有ID
        List<Integer> poolIdList = ipstatusService.getAllId();
        // 遍历ID
        for (Integer poolId : poolIdList) {
            // 获取该ID下所有vmId为null且status为0的IP数量
            QueryWrapper<Ippool> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("pool_id",poolId);
            queryWrapper.eq("status",0);
            int available = ippoolService.getIpCountByCondition(queryWrapper);

            // 获取该ID下所有IP数量
            long allCount = ippoolService.getIpCountByPoolId(poolId);
            // 计算无效IP数量
            long disable = allCount - available;
            // 获取该ID下所有vmId不为null且status为1的IP数量
            queryWrapper.clear();
            queryWrapper.eq("pool_id",poolId);
            queryWrapper.eq("status",1);
            int used = ippoolService.getIpCountByCondition(queryWrapper);
            Ipstatus ipstatus = new Ipstatus();
            ipstatus.setId(poolId);
            ipstatus.setAvailable(available);
            ipstatus.setDisable((int) disable);
            ipstatus.setUsed(used);
            // 更新数据
            ipstatusService.updateById(ipstatus);
        }
    }
}
