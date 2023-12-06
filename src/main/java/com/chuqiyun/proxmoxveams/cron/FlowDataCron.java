package com.chuqiyun.proxmoxveams.cron;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.FlowService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.utils.TimeUtil;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author mryunqi
 * @date 2023/12/6
 */
@Component
@EnableScheduling
public class FlowDataCron {
    @Resource
    private FlowService flowService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private MasterService masterService;

    /**
    * @Author: mryunqi
    * @Description: 定时插入流量数据
    * @DateTime: 2023/12/6 14:42
    */
    @Async
    @Scheduled(fixedRate = 1000*60*5) // 5分钟执行一次
    public void insertFlowdataCron() {
        int i = 1;
        while (true){
            QueryWrapper<Vmhost> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("status", 0); // 只获取开机的虚拟机
            // 分页获取100台虚拟机
            Page<Vmhost> vmhostPage = vmhostService.selectPage(i, 100, queryWrapper);
            List<Vmhost> vmhostList = vmhostPage.getRecords();
            // 如果为空或者等于0，跳出循环
            if (vmhostList == null || vmhostList.size() == 0) {
                break;
            }
            // 遍历虚拟机
            for (Vmhost vmhost : vmhostList) {
                // 判断节点是否在线
                if (!masterService.isNodeOnline(vmhost.getNodeid())){
                    continue;
                }
                flowService.insertFlowdata(vmhost.getId());
            }
            // 如果当前页数等于总页数则跳出循环
            if (i == vmhostPage.getPages()){
                break;
            }
            i++;
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 定时同步流量数据
    * @DateTime: 2023/12/6 14:43
    */
    @Async
    @Scheduled(fixedRate = 1000*60*5) // 5分钟执行一次
    public void syncFlowdataCron() {
        int i = 1;
        while (true){
            QueryWrapper<Vmhost> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("status", 0); // 只获取开机的虚拟机
            // 分页获取100台虚拟机
            Page<Vmhost> vmhostPage = vmhostService.selectPage(i, 100, queryWrapper);
            List<Vmhost> vmhostList = vmhostPage.getRecords();
            // 如果为空或者等于0，跳出循环
            if (vmhostList == null || vmhostList.size() == 0) {
                break;
            }
            // 遍历虚拟机
            for (Vmhost vmhost : vmhostList) {
                // 判断节点是否在线
                if (!masterService.isNodeOnline(vmhost.getNodeid())){
                    continue;
                }
                flowService.syncVmFlowdata(null,vmhost);
            }
            // 如果当前页数等于总页数则跳出循环
            if (i == vmhostPage.getPages()){
                break;
            }
            i++;
        }
    }
    
    /**
    * @Author: mryunqi
    * @Description: 虚拟机流量超限监听
    * @DateTime: 2023/12/6 15:43
    */
    @Async
    @Scheduled(fixedRate = 1000*60*5) // 5分钟执行一次
    public void vmFlowLimitCron() {
        int i = 1;
        while (true){
            QueryWrapper<Vmhost> queryWrapper = new QueryWrapper<>();
            // 获取状态不为4或不为15或不为5的虚拟机 4：暂停 5：到期 15：流量超限
            queryWrapper.ne("status", 4).ne("status", 15).ne("status", 5);
            // 分页获取100台虚拟机
            Page<Vmhost> vmhostPage = vmhostService.selectPage(i, 100, queryWrapper);
            List<Vmhost> vmhostList = vmhostPage.getRecords();
            // 如果为空或者等于0，跳出循环
            if (vmhostList == null || vmhostList.size() == 0) {
                break;
            }
            // 遍历虚拟机
            for (Vmhost vmhost : vmhostList) {
                // 判断节点是否在线
                if (!masterService.isNodeOnline(vmhost.getNodeid())){
                    continue;
                }
                // 虚拟机流量上限
                long flowLimit = vmhost.getFlowLimit();
                // 如果为0，跳出循环
                if (flowLimit == 0){
                    continue;
                }
                // 虚拟机已用流量
                double usedFlow = vmhost.getUsedFlow();
                // 如果已用流量大于流量上限
                if (usedFlow > flowLimit){
                    // 暂停虚拟机
                    vmhostService.power(vmhost.getId(),"qosPause",null);
                    // 修改虚拟机状态为流量超限
                    vmhost.setStatus(15);
                    vmhost.setPauseInfo("流量超限");
                    vmhostService.updateById(vmhost);
                }
            }
            // 如果当前页数等于总页数则跳出循环
            if (i == vmhostPage.getPages()){
                break;
            }
            i++;
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 虚拟机流量超限恢复监听
    * @DateTime: 2023/12/6 17:08
    */
    @Async
    @Scheduled(fixedRate = 1000*60) // 1分钟执行一次
    public void vmQosUnpauseCron(){
        int i = 1;
        while (true){
            QueryWrapper<Vmhost> queryWrapper = new QueryWrapper<>();
            // 获取状态为15的虚拟机 15：流量超限
            queryWrapper.ne("status", 4).eq("status", 15).ne("status", 5);
            // 分页获取100台虚拟机
            Page<Vmhost> vmhostPage = vmhostService.selectPage(i, 100, queryWrapper);
            List<Vmhost> vmhostList = vmhostPage.getRecords();
            // 如果为空或者等于0，跳出循环
            if (vmhostList == null || vmhostList.size() == 0) {
                break;
            }
            // 遍历虚拟机
            for (Vmhost vmhost : vmhostList) {
                // 判断节点是否在线
                if (!masterService.isNodeOnline(vmhost.getNodeid())){
                    continue;
                }
                // 虚拟机流量上限
                long flowLimit = vmhost.getFlowLimit();
                // 如果为0
                if (flowLimit == 0){
                    vmhostService.power(vmhost.getId(),"unpause",null); // 恢复虚拟机
                    vmhost.setPauseInfo(null);
                    vmhost.setStatus(0);
                    vmhostService.updateById(vmhost);
                }
                // 虚拟机已用流量
                double usedFlow = vmhost.getUsedFlow();
                // 如果已用流量小于流量上限
                if (usedFlow < flowLimit){
                    vmhostService.power(vmhost.getId(),"unpause",null); // 恢复虚拟机
                    vmhost.setPauseInfo(null);
                    vmhost.setStatus(0);
                    vmhostService.updateById(vmhost);
                }
            }
            // 如果当前页数等于总页数则跳出循环
            if (i == vmhostPage.getPages()){
                break;
            }
            i++;
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 月初流量重置定时任务
    * @DateTime: 2023/12/6 18:17
    */
    @Async
    @Scheduled(cron = "0 0 0 1 * ?") // 每月1号0点0分0秒执行
    public void vmFlowResetCron() {
        int i = 1;
        while (true) {
            // 分页获取100台虚拟机
            Page<Vmhost> vmhostPage = vmhostService.selectPage(i, 100);
            List<Vmhost> vmhostList = vmhostPage.getRecords();
            // 如果为空或者等于0，跳出循环
            if (vmhostList == null || vmhostList.size() == 0) {
                break;
            }
            // 遍历虚拟机
            for (Vmhost vmhost : vmhostList) {
                vmhost.setUsedFlow(0.00); // 已用流量重置为0.00
                vmhost.setLastResetFlow(System.currentTimeMillis()); // 重置时间
                vmhostService.updateById(vmhost);
            }
            // 如果当前页数等于总页数则跳出循环
            if (i == vmhostPage.getPages()) {
                break;
            }
            i++;
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 遗漏重置流量
    * @DateTime: 2023/12/6 20:04
    */
    @Async
    @Scheduled(fixedRate = 1000*5) // 5分钟执行一次
    public void vmFlowResetCron2() {
        int i = 1;
        while (true) {
            // 分页获取100台虚拟机
            Page<Vmhost> vmhostPage = vmhostService.selectPage(i, 100);
            List<Vmhost> vmhostList = vmhostPage.getRecords();
            // 如果为空或者等于0，跳出循环
            if (vmhostList == null || vmhostList.size() == 0) {
                break;
            }
            // 遍历虚拟机
            for (Vmhost vmhost : vmhostList) {
                // 上一次重置时间戳
                long lastResetTime = vmhost.getLastResetFlow();
                // 如果为空，则重置流量
                if (lastResetTime == 0) {
                    vmhost.setUsedFlow(0.00); // 已用流量重置为0.00
                    vmhost.setLastResetFlow(System.currentTimeMillis()); // 重置时间
                    vmhostService.updateById(vmhost);
                    continue;
                }
                // 判断是否为本月1号之前的
                if (TimeUtil.isLessThanThirtyDays(lastResetTime)) {
                    // 重置流量
                    vmhost.setUsedFlow(0.00); // 已用流量重置为0.00
                    vmhost.setLastResetFlow(System.currentTimeMillis()); // 重置时间
                    vmhostService.updateById(vmhost);
                }
            }
            // 如果当前页数等于总页数则跳出循环
            if (i == vmhostPage.getPages()) {
                break;
            }
            i++;
        }
    }
}
