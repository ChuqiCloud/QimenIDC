package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.chuqiyun.proxmoxveams.entity.Flowdata;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.*;
import com.chuqiyun.proxmoxveams.utils.TimeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author mryunqi
 * @date 2023/12/3
 */
@Service("flowService")
public class FlowServiceImpl implements FlowService {
    private static final int FLOWDATA_SYNC_BATCH_SIZE = 500;

    @Resource
    private FlowdataService flowdataService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private MasterService masterService;
    @Resource
    private VmInfoService vmInfoService;

    /**
    * @Author: mryunqi
    * @Description: 插入新的流量临表数据
    * @DateTime: 2023/12/3 21:24
    * @Params:
    * @Return Boolean true=成功;false=失败
    */
    @Override
    public Boolean insertFlowdata(Integer hostId) {
        // 获取该主机的最新流量临表数据
        Flowdata flowdata = flowdataService.selectFlowdataByHostid(hostId);
        // 获取该主机的信息
        Vmhost vmhost = vmhostService.getById(hostId);
        // 判空
        if (vmhost == null) {
            return false;
        }
        // 判空
        if (flowdata == null) {
            // 筛选出最大的时间戳
            JSONObject hourHistoryData = vmInfoService.getVmInfoRrdData(hostId, "hour", "AVERAGE");
            // 判空
            if (hourHistoryData == null) {
                return false;
            }
            JSONArray hourHistoryDataArray = hourHistoryData.getJSONArray("data"); // 获取小时历史数据数组
            if (hourHistoryDataArray == null || hourHistoryDataArray.isEmpty()) {
                return false;
            }
            Map<String,String> hourHistoryDataMap = new HashMap<>();
            long maxTime = getMaxRrdTime(hourHistoryDataArray);
            if (maxTime == 0L) {
                return false;
            }
            // 创建流量临表对象
            Flowdata newFlowdata = new Flowdata();
            newFlowdata.setNodeId(vmhost.getNodeid());
            newFlowdata.setHostid(hostId);
            newFlowdata.setRrd(hourHistoryDataMap);
            newFlowdata.setUsedFlow(0.00);
            newFlowdata.setCreateDate(maxTime);
            // 插入新的流量临表数据
            flowdataService.insertFlowdata(newFlowdata);
            return false;
        }
        // 该主机获取到的最新流量临表同步时间戳
        Long flowdataTimestamp = flowdata.getCreateDate() == null ? 0L : flowdata.getCreateDate();
        // 获取该主机的小时历史数据
        JSONObject hourHistoryData = vmInfoService.getVmInfoRrdData(hostId, "hour", "AVERAGE");
        // 判空
        if (hourHistoryData == null) {
            return false;
        }
        JSONArray hourHistoryDataArray = hourHistoryData.getJSONArray("data"); // 获取小时历史数据数组
        if (hourHistoryDataArray == null || hourHistoryDataArray.isEmpty()) {
            return false;
        }
        Map<String,String> hourHistoryDataMap = new HashMap<>();
        BigDecimal usedFlow = BigDecimal.ZERO;

        for (int i = 0; i < hourHistoryDataArray.size(); i++) {
            JSONObject hourHistoryDataObject = hourHistoryDataArray.getJSONObject(i);
            BigDecimal currentTime = getRrdTime(hourHistoryDataObject);
            if (currentTime == null) {
                continue;
            }
            // 转为为13位时间戳
            long time = TimeUtil.tenToThirteen(currentTime.longValue());
            // 与流量临表同步时间戳比较
            if (time > flowdataTimestamp) {
                BigDecimal lastTime = BigDecimal.valueOf(flowdataTimestamp).divide(BigDecimal.valueOf(1000));
                if (i > 0) {
                    BigDecimal lastRrdTime = getRrdTime(hourHistoryDataArray.getJSONObject(i - 1));
                    if (lastRrdTime != null) {
                        lastTime = lastRrdTime;
                    }
                }
                BigDecimal dtime = currentTime.subtract(lastTime);
                if (dtime.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal netin = getRrdValue(hourHistoryDataObject, "netin");
                BigDecimal netout = getRrdValue(hourHistoryDataObject, "netout");
                BigDecimal netinAndNetout = netin.add(netout).multiply(dtime);
                // 将时间戳作为key，流量作为value存入map
                hourHistoryDataMap.put(Long.toString(time),netinAndNetout.toPlainString());
                usedFlow = usedFlow.add(netinAndNetout);
            }
        }
        if (hourHistoryDataMap.isEmpty()) {
            return false;
        }
        // 筛选出最大的时间戳
        long maxTime = getMaxRrdTime(hourHistoryDataArray);
        if (maxTime == 0L) {
            return false;
        }
        // 创建流量临表对象
        Flowdata newFlowdata = new Flowdata();
        newFlowdata.setNodeId(vmhost.getNodeid());
        newFlowdata.setHostid(hostId);
        newFlowdata.setRrd(hourHistoryDataMap);
        newFlowdata.setUsedFlow(usedFlow.doubleValue());
        newFlowdata.setCreateDate(maxTime);
        // 插入新的流量临表数据
        return flowdataService.insertFlowdata(newFlowdata);
    }

    /**
    * @Author: mryunqi
    * @Description: 同步虚拟机流量数据
    * @DateTime: 2023/12/6 14:45
    * @Params: int hostId 主机id Vmhost vmhost 主机对象
    * @Return Boolean true=成功;false=失败
    */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean syncVmFlowdata(Integer hostId, Vmhost vmhost) {
        return syncVmFlowdata(hostId, vmhost, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean syncVmFlowdata(Integer hostId, Vmhost vmhost, boolean checkNodeOnline) {
        if (hostId == null && vmhost == null) {
            return false;
        }
        Vmhost newVmhost;
        if (hostId == null) {
            newVmhost = vmhost;
        } else {
            newVmhost = vmhostService.getById(hostId);
        }
        // 判空
        if (newVmhost == null) {
            return false;
        }
        // 判断节点是否在线
        if (checkNodeOnline && !masterService.isNodeOnline(newVmhost.getNodeid())) {
            return false;
        }
        BigDecimal totalUsedFlow = BigDecimal.ZERO;
        boolean hasSyncedFlowdata = false;
        while (true) {
            QueryWrapper<Flowdata> flowdataQueryWrapper = new QueryWrapper<>();
            flowdataQueryWrapper.eq("hostid", newVmhost.getId());
            flowdataQueryWrapper.eq("status", 0);// 未同步
            flowdataQueryWrapper.orderByAsc("create_date");
            flowdataQueryWrapper.last("LIMIT " + FLOWDATA_SYNC_BATCH_SIZE);
            List<Flowdata> flowdataList = flowdataService.list(flowdataQueryWrapper);
            if (flowdataList == null || flowdataList.isEmpty()) {
                break;
            }

            List<Integer> ids = flowdataList.stream()
                    .map(Flowdata::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (ids.isEmpty()) {
                break;
            }

            BigDecimal batchUsedFlow = flowdataList.stream()
                    .map(Flowdata::getUsedFlow)
                    .filter(Objects::nonNull)
                    .map(BigDecimal::valueOf)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            UpdateWrapper<Flowdata> updateWrapper = new UpdateWrapper<>();
            updateWrapper.in("id", ids);
            updateWrapper.eq("status", 0);
            updateWrapper.set("status", 1);
            if (!flowdataService.update(updateWrapper)) {
                break;
            }
            totalUsedFlow = totalUsedFlow.add(batchUsedFlow);
            hasSyncedFlowdata = true;
        }

        if (!hasSyncedFlowdata) {
            return true;
        }
        if (totalUsedFlow.compareTo(BigDecimal.ZERO) > 0) {
            newVmhost.setUsedFlow(addFlow(newVmhost.getUsedFlow(), totalUsedFlow));
            if (!vmhostService.updateById(newVmhost)) {
                throw new IllegalStateException("同步虚拟机流量失败: hostId=" + newVmhost.getId());
            }
        }
        return true;
    }

    private Double addFlow(Double currentFlow, BigDecimal increment) {
        BigDecimal current = currentFlow == null ? BigDecimal.ZERO : BigDecimal.valueOf(currentFlow);
        return current.add(increment).doubleValue();
    }

    private long getMaxRrdTime(JSONArray hourHistoryDataArray) {
        long maxTime = 0L;
        for (int i = 0; i < hourHistoryDataArray.size(); i++) {
            BigDecimal time = getRrdTime(hourHistoryDataArray.getJSONObject(i));
            if (time == null) {
                continue;
            }
            maxTime = Math.max(maxTime, TimeUtil.tenToThirteen(time.longValue()));
        }
        return maxTime;
    }

    private BigDecimal getRrdTime(JSONObject hourHistoryDataObject) {
        String time = hourHistoryDataObject.getString("time");
        return parseRrdDecimal(time);
    }

    private BigDecimal getRrdValue(JSONObject hourHistoryDataObject, String key) {
        BigDecimal value = parseRrdDecimal(hourHistoryDataObject.getString(key));
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal parseRrdDecimal(String value) {
        if (value == null || value.trim().isEmpty() || "NaN".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
