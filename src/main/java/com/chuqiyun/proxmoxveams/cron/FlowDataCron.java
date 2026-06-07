package com.chuqiyun.proxmoxveams.cron;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.FlowService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.service.impl.FlowdataServiceImpl;
import com.chuqiyun.proxmoxveams.utils.TimeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author mryunqi
 * @date 2023/12/6
 */
@Component
@EnableScheduling
public class FlowDataCron {
    private static final int PAGE_SIZE = 100;
    private static final long FLOW_COLLECT_INTERVAL = 1000L * 60 * 5;
    private static final long FLOW_SYNC_REPAIR_INTERVAL = 1000L * 60 * 30;
    private static final long FLOW_REPAIR_INTERVAL = 1000L * 60 * 5;

    private final AtomicBoolean flowCollectRunning = new AtomicBoolean(false);
    private final AtomicBoolean flowLimitRunning = new AtomicBoolean(false);
    private final AtomicBoolean flowQosUnpauseRunning = new AtomicBoolean(false);
    private final AtomicBoolean flowResetRepairRunning = new AtomicBoolean(false);
    private final AtomicBoolean flowDayResetRepairRunning = new AtomicBoolean(false);

    @Resource
    private FlowService flowService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private MasterService masterService;
    @Autowired
    private FlowdataServiceImpl flowdataService;

    /**
    * @Author: mryunqi
    * @Description: 定时插入流量数据
    * @DateTime: 2023/12/6 14:42
     */
    @Async
    @Scheduled(fixedDelay = FLOW_COLLECT_INTERVAL) // 5分钟执行一次
    public void insertFlowdataCron() {
        if (!flowCollectRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            int i = 1;
            Map<Integer, Boolean> nodeOnlineCache = new HashMap<>();
            while (true){
                QueryWrapper<Vmhost> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("status", 0); // 只获取开机的虚拟机
                Page<Vmhost> vmhostPage = vmhostService.selectPage(i, PAGE_SIZE, queryWrapper);
                List<Vmhost> vmhostList = vmhostPage.getRecords();
                if (vmhostList == null || vmhostList.isEmpty()) {
                    break;
                }
                for (Vmhost vmhost : vmhostList) {
                    if (!isNodeOnline(vmhost.getNodeid(), nodeOnlineCache)){
                        continue;
                    }
                    flowService.insertFlowdata(vmhost.getId());
                    flowService.syncVmFlowdata(null,vmhost,false);
                }
                if (i == vmhostPage.getPages()){
                    break;
                }
                i++;
            }
        } finally {
            flowCollectRunning.set(false);
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 定时同步流量数据
    * @DateTime: 2023/12/6 14:43
    */
    @Async
    @Scheduled(fixedDelay = FLOW_SYNC_REPAIR_INTERVAL) // 30分钟补偿同步一次
    public void syncFlowdataCron() {
        if (!flowCollectRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            int i = 1;
            Map<Integer, Boolean> nodeOnlineCache = new HashMap<>();
            while (true){
                QueryWrapper<Vmhost> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("status", 0); // 只获取开机的虚拟机
                Page<Vmhost> vmhostPage = vmhostService.selectPage(i, PAGE_SIZE, queryWrapper);
                List<Vmhost> vmhostList = vmhostPage.getRecords();
                if (vmhostList == null || vmhostList.isEmpty()) {
                    break;
                }
                for (Vmhost vmhost : vmhostList) {
                    if (!isNodeOnline(vmhost.getNodeid(), nodeOnlineCache)){
                        continue;
                    }
                    flowService.syncVmFlowdata(null,vmhost,false);
                }
                if (i == vmhostPage.getPages()){
                    break;
                }
                i++;
            }
        } finally {
            flowCollectRunning.set(false);
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
        if (!flowLimitRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            int i = 1;
            Map<Integer, Boolean> nodeOnlineCache = new HashMap<>();
            while (true){
                QueryWrapper<Vmhost> queryWrapper = new QueryWrapper<>();
                // 获取状态不为4或不为15或不为5的虚拟机 4：暂停 5：到期 15：流量超限
                queryWrapper.ne("status", 4).ne("status", 15).ne("status", 5);
                // 分页获取100台虚拟机
                Page<Vmhost> vmhostPage = vmhostService.selectPage(i, PAGE_SIZE, queryWrapper);
                List<Vmhost> vmhostList = vmhostPage.getRecords();
                // 如果为空或者等于0，跳出循环
                if (vmhostList == null || vmhostList.isEmpty()) {
                    break;
                }
                // 遍历虚拟机
                for (Vmhost vmhost : vmhostList) {
                    // 判断节点是否在线
                    if (!isNodeOnline(vmhost.getNodeid(), nodeOnlineCache)){
                        continue;
                    }
                    // 虚拟机流量上限
                    long flowLimit = vmhost.getFlowLimit();
                    long extraFlowLimit = vmhost.getExtraFlowLimit();
                    // 如果为0，跳出循环
                    if (flowLimit == 0){
                        continue;
                    }
                    // 虚拟机已用流量
                    double usedFlow = vmhost.getUsedFlow();
                    // 如果已用流量大于流量上限
                    if (usedFlow > (flowLimit + extraFlowLimit)){
                        if (vmhost.getOutFlow() == 0){
                            // 暂停虚拟机
                            vmhostService.power(vmhost.getId(),"qosPause",null);
                            // 修改虚拟机状态为流量超限
                            vmhost.setStatus(15);
                            vmhost.setPauseInfo("流量超限");
                            vmhostService.updateById(vmhost);
                        } else {
                            vmhost.setStatus(15);
                            vmhost.setPauseInfo("流量超限");
                            vmhostService.updateById(vmhost);
                            double bandWidthValue = vmhost.getOutFlow() / 1024.0 / 8.0;
                            String bandWidth = String.format("%.3f", bandWidthValue);
                            vmhostService.changeVmHostBandWidth(vmhost, bandWidth);
                        }
                    }
                }
                // 如果当前页数等于总页数则跳出循环
                if (i == vmhostPage.getPages()){
                    break;
                }
                i++;
            }
        } finally {
            flowLimitRunning.set(false);
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
        if (!flowQosUnpauseRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            int i = 1;
            Map<Integer, Boolean> nodeOnlineCache = new HashMap<>();
            while (true){
                QueryWrapper<Vmhost> queryWrapper = new QueryWrapper<>();
                // 获取状态为15的虚拟机 15：流量超限 .eq("status", 2).eq("pause_info", "流量超限")
                //queryWrapper.eq("status", 2).eq("pause_info", "流量超限");
                queryWrapper.ne("status", 4).eq("status", 15).ne("status", 5);
                // 分页获取100台虚拟机
                Page<Vmhost> vmhostPage = vmhostService.selectPage(i, PAGE_SIZE, queryWrapper);
                List<Vmhost> vmhostList = vmhostPage.getRecords();
                // 如果为空或者等于0，跳出循环
                if (vmhostList == null || vmhostList.isEmpty()) {
                    break;
                }
                // 遍历虚拟机
                for (Vmhost vmhost : vmhostList) {
                    // 判断节点是否在线
                    if (!isNodeOnline(vmhost.getNodeid(), nodeOnlineCache)){
                        continue;
                    }
                    // 虚拟机流量上限
                    long flowLimit = vmhost.getFlowLimit();
                    long extraFlowLimit = vmhost.getExtraFlowLimit();
                    // 如果为0
                    if (flowLimit == 0){
                        outFlowIsZero(vmhost);
                    }
                    // 虚拟机已用流量
                    double usedFlow = vmhost.getUsedFlow();
                    // 如果已用流量小于流量上限
                    if (usedFlow < (flowLimit + extraFlowLimit)){
                        outFlowIsZero(vmhost);
                    }
                }
                // 如果当前页数等于总页数则跳出循环
                if (i == vmhostPage.getPages()){
                    break;
                }
                i++;
            }
        } finally {
            flowQosUnpauseRunning.set(false);
        }
    }

    private void outFlowIsZero(Vmhost vmhost) {
        if (vmhost.getOutFlow() == 0){
            vmhostService.power(vmhost.getId(),"unpause",null); // 恢复虚拟机
            vmhost.setPauseInfo(null);
            vmhost.setStatus(0);
            vmhostService.updateById(vmhost);
        } else {
            vmhost.setPauseInfo(null);
            vmhost.setStatus(0);
            vmhostService.updateById(vmhost);
            double bandWidthValue = vmhost.getBandwidth() / 8.0;
            String bandWidth = String.format("%.3f", bandWidthValue);
            vmhostService.changeVmHostBandWidth(vmhost,bandWidth);
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
            QueryWrapper<Vmhost> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("reset_flow_time", 1);
            // 分页获取100台虚拟机
            Page<Vmhost> vmhostPage = vmhostService.selectPage(i, PAGE_SIZE, queryWrapper);
            List<Vmhost> vmhostList = vmhostPage.getRecords();
            // 如果为空或者等于0，跳出循环
            if (vmhostList == null || vmhostList.isEmpty()) {
                break;
            }
            // 遍历虚拟机
            for (Vmhost vmhost : vmhostList) {
                resetVmFlow(vmhost);
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
    @Scheduled(fixedDelay = FLOW_REPAIR_INTERVAL) // 5分钟执行一次
    public void vmFlowResetCron2() {
        if (!flowResetRepairRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            int i = 1;
            while (true) {
                QueryWrapper<Vmhost> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("reset_flow_time", 1);
                Page<Vmhost> vmhostPage = vmhostService.selectPage(i, PAGE_SIZE, queryWrapper);
                List<Vmhost> vmhostList = vmhostPage.getRecords();
                if (vmhostList == null || vmhostList.isEmpty()) {
                    break;
                }
                for (Vmhost vmhost : vmhostList) {
                    if (vmhost.getResetFlowTime() == 0) continue; // 跳过0 0表示开通日
                    long lastResetTime = vmhost.getLastResetFlow();
                    if (lastResetTime == 0 || TimeUtil.isLessThanThirtyDays(lastResetTime)) {
                        resetVmFlow(vmhost);
                    }
                }
                if (i == vmhostPage.getPages()) {
                    break;
                }
                i++;
            }
        } finally {
            flowResetRepairRunning.set(false);
        }
    }
    /**
     * @Author: 星禾
     * @Description: 开通日流量重置定时任务
     * @DateTime: 2025/11/25 09:25
     */
    @Async
    @Scheduled(cron = "0 0 0 * * ?") // 每天0点执行
    public void vmFlowDayResetCron() {
        int i = 1;
        while (true) {
            QueryWrapper<Vmhost> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("reset_flow_time", 0);
            Page<Vmhost> vmhostPage = vmhostService.selectPage(i, PAGE_SIZE, queryWrapper);
            List<Vmhost> vmhostList = vmhostPage.getRecords();
            if (vmhostList == null || vmhostList.isEmpty()) break;

            for (Vmhost vmhost : vmhostList) {
                // 处理开通日重置逻辑
                long createTime = vmhost.getCreateTime();
                LocalDate createDate = Instant.ofEpochMilli(createTime)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();

                // 计算有效重置日（自动处理31日等特殊情况）
                LocalDate nextResetDate = calculateResetDate(createDate);
                LocalDate today = LocalDate.now();

                // 仅在匹配日期执行重置
                if (today.equals(nextResetDate)) {
                    resetVmFlow(vmhost);
                }
            }

            if (i == vmhostPage.getPages()) break;
            i++;
        }
    }
    /**
     * @Author: 星禾
     * @Description: 遗漏重置流量2
     * @DateTime: 2025/12/1 16:04
     */
    @Async
    @Scheduled(fixedDelay = FLOW_REPAIR_INTERVAL) // 5分钟执行一次
    public void vmFlowDayResetCron2() {
        if (!flowDayResetRepairRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            int i = 1;
            while (true) {
                QueryWrapper<Vmhost> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("reset_flow_time", 0);
                Page<Vmhost> vmhostPage = vmhostService.selectPage(i, PAGE_SIZE, queryWrapper);
                List<Vmhost> vmhostList = vmhostPage.getRecords();
                if (vmhostList == null || vmhostList.isEmpty()) {
                    break;
                }
                for (Vmhost vmhost : vmhostList) {
                    long createTime = vmhost.getCreateTime();
                    if (TimeUtil.isSameDay(createTime)) {
                        continue;
                    }
                    LocalDate createDate = Instant.ofEpochMilli(createTime)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate();
                    LocalDate nextResetDate = calculateResetDate(createDate);
                    LocalDate today = LocalDate.now();
                    if (today.equals(nextResetDate)) {
                        long lastResetTime = vmhost.getLastResetFlow();
                        if (lastResetTime == 0 || !TimeUtil.isSameDay(lastResetTime)) {
                            resetVmFlow(vmhost);
                        }
                    }
                }
                if (i == vmhostPage.getPages()) {
                    break;
                }
                i++;
            }
        } finally {
            flowDayResetRepairRunning.set(false);
        }
    }

    /**
     * @Author: 星禾
     * @Description 计算有效重置日期（自动处理月末情况）
     * @param createDate 开通日期
     * @return 次月有效重置日期
     */
    private LocalDate calculateResetDate(LocalDate createDate) {
        // 获取开通日（1-31）
        int dayOfMonth = createDate.getDayOfMonth();
        // 特殊处理31日开通情况
        if (dayOfMonth > 28) {
            // 获取次月最后一天
            LocalDate nextMonth = createDate.plusMonths(1);
            return nextMonth.with(TemporalAdjusters.lastDayOfMonth());
        }
        // 常规情况：次月同一天
        return createDate.plusMonths(1).withDayOfMonth(dayOfMonth);
    }

    private void resetVmFlow(Vmhost vmhost) {
        vmhost.setUsedFlow(0.00);
        vmhost.setExtraFlowLimit(0L);
        vmhost.setLastResetFlow(System.currentTimeMillis());
        vmhostService.updateById(vmhost);
    }

    private boolean isNodeOnline(Integer nodeId, Map<Integer, Boolean> nodeOnlineCache) {
        if (nodeId == null) {
            return false;
        }
        return nodeOnlineCache.computeIfAbsent(nodeId, masterService::isNodeOnline);
    }

    /**
     * @Author: 星禾
     * @Description: 定时清理浏览统计数据
     * @DateTime: 2026/4/21 17:22
     */
    @Async
    @Scheduled(fixedRate = 1000*60*60) // 60分钟执行一次
    public void CleanFlowdataCron() {
        flowdataService.deleteExpiredFlowData();
    }
}
