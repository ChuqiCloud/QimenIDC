package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.entity.Flowdata;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.*;
import com.chuqiyun.proxmoxveams.utils.TimeUtil;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author mryunqi
 * @date 2023/12/3
 */
@Service("flowService")
public class FlowServiceImpl implements FlowService {
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
            Map<String,String> hourHistoryDataMap = new HashMap<>();
            long maxTime = 0L;
            maxTime = hourHistoryDataArray.stream()
                    .mapToLong(item -> Long.parseLong(((JSONObject) item).getString("time")))
                    .map(TimeUtil::tenToThirteen)
                    .max()
                    .orElse(0L);
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
        Long flowdataTimestamp = flowdata.getCreateDate();
        // 获取该主机的小时历史数据
        JSONObject hourHistoryData = vmInfoService.getVmInfoRrdData(hostId, "hour", "AVERAGE");
        // 判空
        if (hourHistoryData == null) {
            return false;
        }
        JSONArray hourHistoryDataArray = hourHistoryData.getJSONArray("data"); // 获取小时历史数据数组
        Map<String,String> hourHistoryDataMap = new HashMap<>();

        for (int i = 0; i < hourHistoryDataArray.size(); i++) {
            JSONObject hourHistoryDataObject = hourHistoryDataArray.getJSONObject(i);
            // 转为为13位时间戳
            long time = TimeUtil.tenToThirteen(Long.parseLong(hourHistoryDataObject.getString("time")));
            // 与流量临表同步时间戳比较
            if (time > flowdataTimestamp) {
                // 判断是否存在netin和netout的键值对
                if (hourHistoryDataObject.containsKey("netin") && hourHistoryDataObject.containsKey("netout")) {
                    // netin 143.926666666667
                    // 将netin与netout数据准确相加
                    String netin = hourHistoryDataObject.getString("netin");
                    String netout = hourHistoryDataObject.getString("netout");
                    // 使用BigDecimal进行精确计算
                    BigDecimal netinBigDecimal = new BigDecimal(netin);
                    BigDecimal netoutBigDecimal = new BigDecimal(netout);
                    String netinAndNetout = netinBigDecimal.add(netoutBigDecimal).toString();
                    // 将时间戳作为key，流量作为value存入map
                    hourHistoryDataMap.put(Long.toString(time),netinAndNetout);
                }else{
                    // 值为0
                    hourHistoryDataMap.put(Long.toString(time),"0");
                }
            }
        }
        double usedFlow = 0.00;
        // 判断map是否为空
        if (hourHistoryDataMap.size() > 0) {
            // 遍历map
            for (Map.Entry<String, String> entry : hourHistoryDataMap.entrySet()) {
                // 将流量累加
                usedFlow += Double.parseDouble(entry.getValue());
            }
        }
        // 筛选出最大的时间戳
        long maxTime = 0L;
        maxTime = hourHistoryDataArray.stream()
                .mapToLong(item -> Long.parseLong(((JSONObject) item).getString("time")))
                .map(TimeUtil::tenToThirteen)
                .max()
                .orElse(0L);
        // 创建流量临表对象
        Flowdata newFlowdata = new Flowdata();
        newFlowdata.setNodeId(vmhost.getNodeid());
        newFlowdata.setHostid(hostId);
        newFlowdata.setRrd(hourHistoryDataMap);
        newFlowdata.setUsedFlow(usedFlow);
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
    public Boolean syncVmFlowdata(Integer hostId, Vmhost vmhost) {
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
        if (!masterService.isNodeOnline(newVmhost.getNodeid())) {
            return false;
        }
        QueryWrapper<Flowdata> flowdataQueryWrapper = new QueryWrapper<>();
        flowdataQueryWrapper.eq("hostid", newVmhost.getId());
        flowdataQueryWrapper.eq("status", 0);// 未同步
        int i = 1;
        while (true) {
            // 分页获取100条流量临表数据
            Page<Flowdata> flowdataPage = flowdataService.selectFlowdataByPageAndWrapper(i, 100, flowdataQueryWrapper);
            List<Flowdata> flowdataList = flowdataPage.getRecords();
            // 如果为空或者等于0，跳出循环
            if (flowdataList == null || flowdataList.size() == 0) {
                break;
            }
            // 遍历流量临表数据
            for (Flowdata flowdata : flowdataList) {
                // 更新虚拟机流量数据
                newVmhost.setUsedFlow(newVmhost.getUsedFlow() + flowdata.getUsedFlow());
                // 更新流量临表数据状态
                flowdata.setStatus(1);
                flowdataService.updateById(flowdata);
            }
            // 如果当前页数等于总页数则跳出循环
            if (i == flowdataPage.getPages()) {
                break;
            }
            i++;
        }
        // 更新虚拟机流量数据
        return vmhostService.updateById(newVmhost);
    }
}
