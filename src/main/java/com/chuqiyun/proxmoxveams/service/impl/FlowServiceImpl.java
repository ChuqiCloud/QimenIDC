package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.chuqiyun.proxmoxveams.entity.Flowdata;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.*;
import com.chuqiyun.proxmoxveams.utils.FlowTypeUtil;
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
    private static final long MAX_RRD_BASELINE_GAP_SECONDS = 10 * 60L;
    private static final String COUNTER_NETIN_KEY = "__counter_netin";
    private static final String COUNTER_NETOUT_KEY = "__counter_netout";
    private static final String COUNTER_TIME_KEY = "__counter_time";

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
        Flowdata flowdata = flowdataService.selectFlowdataByHostid(hostId);
        Vmhost vmhost = vmhostService.getById(hostId);
        if (vmhost == null) {
            return false;
        }

        long collectTime = System.currentTimeMillis();
        FlowCounter currentCounter = getCurrentFlowCounter(hostId, collectTime);
        FlowCounter previousCounter = getSavedFlowCounter(flowdata);
        if (currentCounter != null && previousCounter != null && !currentCounter.isResetFrom(previousCounter)) {
            BigDecimal counterIncrement = FlowTypeUtil.calculate(
                    currentCounter.netin.subtract(previousCounter.netin),
                    currentCounter.netout.subtract(previousCounter.netout),
                    vmhost.getFlowType());
            return flowdataService.insertFlowdata(
                    buildCounterFlowdata(vmhost, hostId, currentCounter, counterIncrement));
        }

        long baselineTimestamp = flowdata == null || flowdata.getCreateDate() == null
                ? 0L : flowdata.getCreateDate();
        JSONObject hourHistoryData = vmInfoService.getVmInfoRrdData(hostId, "hour", "AVERAGE");
        JSONArray hourHistoryDataArray = hourHistoryData == null ? null : hourHistoryData.getJSONArray("data");
        Flowdata newFlowdata = null;
        if (hourHistoryDataArray != null && !hourHistoryDataArray.isEmpty()) {
            newFlowdata = buildFlowdataFromRrd(vmhost, hostId, hourHistoryDataArray, baselineTimestamp);
        }

        if (newFlowdata == null && currentCounter != null && previousCounter != null
                && currentCounter.isResetFrom(previousCounter)) {
            newFlowdata = buildCounterFlowdata(vmhost, hostId, currentCounter,
                    FlowTypeUtil.calculate(currentCounter.netin, currentCounter.netout, vmhost.getFlowType()));
        }

        if (newFlowdata == null && currentCounter != null && flowdata == null
                && (vmhost.getUsedFlow() == null || vmhost.getUsedFlow() == 0D)) {
            newFlowdata = buildCounterFlowdata(vmhost, hostId, currentCounter,
                    FlowTypeUtil.calculate(currentCounter.netin, currentCounter.netout, vmhost.getFlowType()));
        }

        if (newFlowdata == null && currentCounter != null) {
            newFlowdata = buildEmptyFlowdata(vmhost, hostId, collectTime);
        }
        if (newFlowdata == null) {
            return false;
        }

        if (currentCounter != null) {
            saveFlowCounter(newFlowdata, currentCounter);
            newFlowdata.setCreateDate(collectTime);
        }
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

    private Flowdata buildFlowdataFromRrd(Vmhost vmhost, Integer hostId, JSONArray hourHistoryDataArray, long baselineTimestamp) {
        Map<String,String> hourHistoryDataMap = new HashMap<>();
        BigDecimal usedFlow = BigDecimal.ZERO;
        BigDecimal baselineTime = baselineTimestamp <= 0
                ? null : BigDecimal.valueOf(baselineTimestamp).divide(BigDecimal.valueOf(1000));
        BigDecimal lastRrdTime = null;
        long maxTime = 0L;

        for (int i = 0; i < hourHistoryDataArray.size(); i++) {
            JSONObject hourHistoryDataObject = hourHistoryDataArray.getJSONObject(i);
            BigDecimal currentTime = getRrdTime(hourHistoryDataObject);
            if (currentTime == null) {
                continue;
            }
            long time = TimeUtil.tenToThirteen(currentTime.longValue());
            maxTime = Math.max(maxTime, time);
            if (time <= baselineTimestamp) {
                lastRrdTime = currentTime;
                continue;
            }
            BigDecimal startTime = lastRrdTime;
            if (baselineTime != null && (startTime == null || startTime.compareTo(baselineTime) < 0)) {
                BigDecimal baselineGap = currentTime.subtract(baselineTime);
                startTime = lastRrdTime == null
                        && baselineGap.compareTo(BigDecimal.valueOf(MAX_RRD_BASELINE_GAP_SECONDS)) > 0
                        ? null : baselineTime;
            }
            if (startTime != null) {
                BigDecimal dtime = currentTime.subtract(startTime);
                if (dtime.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal netin = getRrdValue(hourHistoryDataObject, "netin");
                    BigDecimal netout = getRrdValue(hourHistoryDataObject, "netout");
                    BigDecimal flow = FlowTypeUtil.calculate(netin, netout, vmhost.getFlowType()).multiply(dtime);
                    hourHistoryDataMap.put(Long.toString(time), flow.toPlainString());
                    usedFlow = usedFlow.add(flow);
                }
            }
            lastRrdTime = currentTime;
        }

        if (hourHistoryDataMap.isEmpty() || maxTime == 0L) {
            return null;
        }
        Flowdata newFlowdata = buildEmptyFlowdata(vmhost, hostId, maxTime);
        newFlowdata.setRrd(hourHistoryDataMap);
        newFlowdata.setUsedFlow(usedFlow.doubleValue());
        return newFlowdata;
    }

    private Flowdata buildCounterFlowdata(Vmhost vmhost, Integer hostId, FlowCounter counter, BigDecimal increment) {
        Flowdata newFlowdata = buildEmptyFlowdata(vmhost, hostId, counter.collectTime);
        BigDecimal normalizedIncrement = increment == null || increment.compareTo(BigDecimal.ZERO) < 0
                ? BigDecimal.ZERO : increment;
        newFlowdata.setUsedFlow(normalizedIncrement.doubleValue());
        newFlowdata.getRrd().put(Long.toString(counter.collectTime), normalizedIncrement.toPlainString());
        saveFlowCounter(newFlowdata, counter);
        return newFlowdata;
    }

    private FlowCounter getCurrentFlowCounter(Integer hostId, long collectTime) {
        JSONObject response = vmInfoService.getVmCurrentStatus(hostId);
        if (response == null) {
            return null;
        }
        JSONObject data = response.getJSONObject("data");
        if (data == null) {
            data = response;
        }
        BigDecimal netin = parseRrdDecimal(data.getString("netin"));
        BigDecimal netout = parseRrdDecimal(data.getString("netout"));
        if (netin == null && netout == null) {
            return null;
        }
        return new FlowCounter(netin == null ? BigDecimal.ZERO : netin.max(BigDecimal.ZERO),
                netout == null ? BigDecimal.ZERO : netout.max(BigDecimal.ZERO), collectTime);
    }

    private FlowCounter getSavedFlowCounter(Flowdata flowdata) {
        if (flowdata == null || flowdata.getRrd() == null) {
            return null;
        }
        BigDecimal netin = parseRrdDecimal(flowdata.getRrd().get(COUNTER_NETIN_KEY));
        BigDecimal netout = parseRrdDecimal(flowdata.getRrd().get(COUNTER_NETOUT_KEY));
        BigDecimal collectTime = parseRrdDecimal(flowdata.getRrd().get(COUNTER_TIME_KEY));
        if (netin == null || netout == null || collectTime == null) {
            return null;
        }
        return new FlowCounter(netin, netout, collectTime.longValue());
    }

    private void saveFlowCounter(Flowdata flowdata, FlowCounter counter) {
        Map<String, String> rrd = flowdata.getRrd();
        if (rrd == null) {
            rrd = new HashMap<>();
            flowdata.setRrd(rrd);
        }
        rrd.put(COUNTER_NETIN_KEY, counter.netin.toPlainString());
        rrd.put(COUNTER_NETOUT_KEY, counter.netout.toPlainString());
        rrd.put(COUNTER_TIME_KEY, Long.toString(counter.collectTime));
    }

    private Flowdata buildEmptyFlowdata(Vmhost vmhost, Integer hostId, long createDate) {
        Flowdata newFlowdata = new Flowdata();
        newFlowdata.setNodeId(vmhost.getNodeid());
        newFlowdata.setHostid(hostId);
        newFlowdata.setRrd(new HashMap<>());
        newFlowdata.setUsedFlow(0.00);
        newFlowdata.setCreateDate(createDate);
        return newFlowdata;
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

    private static class FlowCounter {
        private final BigDecimal netin;
        private final BigDecimal netout;
        private final long collectTime;

        private FlowCounter(BigDecimal netin, BigDecimal netout, long collectTime) {
            this.netin = netin;
            this.netout = netout;
            this.collectTime = collectTime;
        }

        private boolean isResetFrom(FlowCounter previous) {
            return netin.compareTo(previous.netin) < 0 || netout.compareTo(previous.netout) < 0;
        }
    }
}
