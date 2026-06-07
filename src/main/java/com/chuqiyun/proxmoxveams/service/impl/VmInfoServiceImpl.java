package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.dto.VmHostDto;
import com.chuqiyun.proxmoxveams.entity.Area;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Os;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.*;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import com.chuqiyun.proxmoxveams.utils.VmUtil;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * @author mryunqi
 * @date 2023/8/23
 */
@Service("vmInfoService")
public class VmInfoServiceImpl implements VmInfoService {
    private static final String[] VM_RRD_TIMEFRAMES = {"hour", "day", "week", "month"};

    @Resource
    private MasterService masterService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private OsService osService;
    @Resource
    private AreaService areaService;

    /**
    * @Author: mryunqi
    * @Description: 获取虚拟机分页列表
    * @DateTime: 2023/8/23 21:50
    * @Params: Integer page 页码，Integer size 每页数量
    * @Return HashMap<String, Object> 分页列表
    */
    @Override
    public HashMap<String, Object> getVmByPage(Integer page, Integer size) {
        Page<Vmhost> vmhostPage = vmhostService.selectPage(page, size);
        HashMap<String, Object> pageMap = new HashMap<>();
        buildVmHostDto(vmhostPage,pageMap);
        return pageMap;
    }

    /**
     * @Author: 星禾
     * @Description: 获取回收站虚拟机分页列表
     * @DateTime: 2026/5/23 22:50
     * @Params: Integer page 页码，Integer size 每页数量
     * @Return HashMap<String, Object> 分页列表
     */
    @Override
    public HashMap<String, Object> getDeleteVmByPage(Integer page, Integer size) {
        Page<Vmhost> vmhostPage = vmhostService.selectPageByDelete(page, size);
        HashMap<String, Object> pageMap = new HashMap<>();
        buildVmHostDto(vmhostPage,pageMap);
        return pageMap;
    }

    /**
    * @Author: mryunqi
    * @Description: 获取虚拟机分页列表,根据创建时间降序排列
    * @DateTime: 2023/11/26 20:13
    * @Params: Integer page 页码，Integer size 每页数量
    * @Return  HashMap<String, Object> 分页列表
    */
    @Override
    public HashMap<String, Object> getVmByPageOrderByCreateTime(Integer page, Integer size) {
        Page<Vmhost> vmhostPage = vmhostService.selectPageByCreateTime(page, size);
        HashMap<String, Object> pageMap = new HashMap<>();
        buildVmHostDto(vmhostPage,pageMap);
        return pageMap;
    }

    /**
    * @Author: mryunqi
    * @Description: 获取虚拟机总数
    * @DateTime: 2023/11/26 20:21
    * @Return Long 虚拟机总数
    */
    @Override
    public Long getVmCount() {
        return vmhostService.count();
    }

    /**
    * @Author: mryunqi
    * @Description: 分页查询指定参数的虚拟机
    * @DateTime: 2023/8/24 16:07
    * @Params: Integer page 页码，Integer size 每页数量，String param 查询参数，String value 查询值
    * @Return HashMap<String,Object> 分页列表
    */
    @Override
    public Object getVmHostPageByParam(Integer page, Integer size, String param, String value) {
        // 如果参数为空，则查询全部
        if (param == null || value == null){
            return getVmByPage(page, size);
        }
        // 如果参数为IP
        if (param.equals("ip")){
            Page<Vmhost> vmhostPage = vmhostService.selectPageByIp(page, size, value);
            HashMap<String, Object> pageMap = new HashMap<>();
            buildVmHostDto(vmhostPage,pageMap);
            return pageMap;
        }
        // 如果参数为主机名
        if (param.equals("hostname")){
            return vmhostService.getVmhostByName(page,size,value);
        }
        // 如果参数为虚拟机ID
        if (param.equals("vmid")){
            return vmhostService.getVmhostByVmId(Integer.parseInt(value));
        }
        // 如果参数为节点ID
        if (param.equals("nodeid")){
            Page<Vmhost> vmhostPage = vmhostService.selectPageByNodeId(page, size, value);
            HashMap<String, Object> pageMap = new HashMap<>();
            buildVmHostDto(vmhostPage,pageMap);
            return pageMap;
        }
        // 如果参数为状态
        if (param.equals("status")){
            Integer status = VmUtil.statusStrToInt(value);
            Page<Vmhost> vmhostPage = vmhostService.selectPageByStatus(page, size, status);
            HashMap<String, Object> pageMap = new HashMap<>();
            buildVmHostDto(vmhostPage,pageMap);
            return pageMap;
        }
        return getVmByPage(page, size);
    }

    /**
    * @Author: mryunqi
    * @Description: 根据vmid获取虚拟机信息
    * @DateTime: 2023/8/28 19:44
    * @Params: Integer vmId 虚拟机ID
    * @Return VmHostDto 虚拟机信息
    */
    @Override
    public VmHostDto getVmHostById(Integer hostId) {
        Vmhost vmhost = vmhostService.getById(hostId);
        // 如果虚拟机不存在
        if (vmhost == null){
            // 将hostId作为vmid查询
            // vmhost = vmhostService.getVmhostByVmId(hostId);
            // 如果虚拟机不存在
            //if (vmhost == null){
            return null;
            //}
        }
        // 将Map类型的ipConfig转换为HashMap

        vmhost.setIpData(VmUtil.splitIpAddress(new HashMap<>(vmhost.getIpConfig())));
        // 判断osName是否为空
        if (vmhost.getOsName() == null){
            Os vmOs = osService.isExistOs(vmhost.getOs());
            if (vmOs != null) {
                vmhost.setOsName(vmOs.getName());
            }
        }
        VmHostDto vmHostDto = new VmHostDto();
        vmHostDto.setVmhost(vmhost);
        Integer nodeId = vmhost.getNodeid();
        Master node = masterService.getById(nodeId);
        if (node.getArea() == null){
            vmHostDto.setArea(null);
        }else {
            Area area = areaService.getById(node.getArea());
            vmHostDto.setArea(area.getName());
        }
        Os os = osService.isExistOs(vmhost.getOsName());
        vmHostDto.setOs(os);
        // 获取cookie
        HashMap<String, String> cookieMap = masterService.getMasterCookieMap(nodeId);
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        vmHostDto.setRrddata(getVmInfoRrdDataAllTime(hostId, "AVERAGE"));
        try {
            vmHostDto.setCurrent(proxmoxApiUtil.getVmStatus(node, cookieMap, vmhost.getVmid()));
        }catch(Exception e){
            vmHostDto.setCurrent(null);
        }
        return vmHostDto;
    }

    /**
    * @Author: mryunqi
    * @Description: 获取虚拟机历史负载
    * @DateTime: 2023/8/28 20:06
    * @Params: Integer vmId 虚拟机ID，String timeframe 时间范围，String cf 数据类型
    * @Return JSONObject 虚拟机历史负载
    */
    @Override
    public JSONObject getVmInfoRrdData(Integer hostId,String timeframe, String cf){
        Vmhost vmhost = vmhostService.getById(hostId);
        // 如果虚拟机不存在
        if (vmhost == null){
            // 将hostId作为vmid查询
            //vmhost = vmhostService.getVmhostByVmId(hostId);
            // 如果虚拟机不存在
            //if (vmhost == null){
            return null;
            //}
        }
        Integer nodeId = vmhost.getNodeid();
        Master node = masterService.getById(nodeId);
        // 获取cookie
        HashMap<String, String> cookieMap = masterService.getMasterCookieMap(nodeId);
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        // 获取虚拟机信息
        try{
            return proxmoxApiUtil.getVmRrd(node, cookieMap, vmhost.getVmid(),timeframe,cf);
        }catch (Exception e){
            return null;
        }
    }

    /**
     * @Author: 星禾
     * @Description: 获取虚拟机小时、天、周、月历史负载
     * @DateTime: 2026/6/6 21:47
     */
    @Override
    public JSONObject getVmInfoRrdDataAllTime(Integer hostId, String cf) {
        Vmhost vmhost = vmhostService.getById(hostId);
        if (vmhost == null) {
            return null;
        }
        Integer nodeId = vmhost.getNodeid();
        Master node = masterService.getById(nodeId);
        if (node == null) {
            return null;
        }
        HashMap<String, String> cookieMap = masterService.getMasterCookieMap(nodeId);
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        JSONArray mergedDataArray = new JSONArray();
        for (String timeframe : VM_RRD_TIMEFRAMES) {
            JSONArray timeframeDataArray = getVmRrdDataArray(proxmoxApiUtil, node, cookieMap, vmhost.getVmid(), timeframe, cf);
            mergedDataArray.addAll(timeframeDataArray);
        }
        List<JSONObject> sortedDataList = new ArrayList<>();
        for (int i = 0; i < mergedDataArray.size(); i++) {
            JSONObject dataObject = mergedDataArray.getJSONObject(i);
            if (dataObject != null) {
                sortedDataList.add(dataObject);
            }
        }
        sortedDataList.sort(Comparator.comparingLong(this::getRrdTimeValue));
        JSONObject result = new JSONObject();
        result.put("data", new JSONArray(sortedDataList));
        return result;
    }

    private JSONArray getVmRrdDataArray(ProxmoxApiUtil proxmoxApiUtil, Master node, HashMap<String, String> cookieMap, Integer vmid, String timeframe, String cf) {
        try {
            JSONObject rrdData = proxmoxApiUtil.getVmRrd(node, cookieMap, vmid, timeframe, cf);
            if (rrdData == null) {
                return new JSONArray();
            }
            JSONArray dataArray = rrdData.getJSONArray("data");
            return dataArray == null ? new JSONArray() : dataArray;
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private long getRrdTimeValue(JSONObject dataObject) {
        if (dataObject == null) {
            return Long.MAX_VALUE;
        }
        Long timeValue = dataObject.getLong("time");
        return timeValue == null ? Long.MAX_VALUE : timeValue;
    }

    private void buildVmHostDto(Page<Vmhost> vmhostPage,HashMap<String, Object> pageMap){
        List<Vmhost> vmhostList = vmhostPage.getRecords();
        List<VmHostDto> vmHostDtoList = new ArrayList<>();
        // 遍历虚拟机列表
        for (Vmhost vmhost : vmhostList){
            int nodeId = vmhost.getNodeid();
            int vmId = vmhost.getVmid();
            VmHostDto vmHostDto = new VmHostDto();
            JSONObject vmInfo;
            Master node = masterService.getById(nodeId);
            // 判断node是否存在，或者是否可用
            if (node == null || node.getStatus() != 0){
                vmHostDto.setVmhost(vmhost);
                vmHostDto.setCurrent(null);
                Os os = osService.isExistOs(vmhost.getOsName());
                vmHostDto.setOs(os);
                vmHostDtoList.add(vmHostDto);
                continue;
            }
            // 获取cookie
            HashMap<String, String> cookieMap = masterService.getMasterCookieMap(nodeId);
            ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
            // 获取虚拟机实时信息
            try {
                vmInfo = proxmoxApiUtil.getVmStatus(node, cookieMap, vmId);
            } catch (Exception e) {
                vmInfo = null;
            }
            vmHostDto.setVmhost(vmhost);
            vmHostDto.setCurrent(vmInfo);
            vmHostDtoList.add(vmHostDto);
            vmHostDto.setNodeName(node.getName());
            if (node.getArea() == null){
                vmHostDto.setArea(null);
            }else {
                Area area = areaService.getById(node.getArea());
                vmHostDto.setArea(area.getName());
            }
            Os os = osService.isExistOs(vmhost.getOsName());
            vmHostDto.setOs(os);
        }
        // 将Page对象转换为Map
        pageMap.put("total", vmhostPage.getTotal());
        pageMap.put("size", vmhostPage.getSize());
        pageMap.put("current", vmhostPage.getCurrent());
        pageMap.put("pages", vmhostPage.getPages());
        pageMap.put("records", vmHostDtoList);
    }
}
