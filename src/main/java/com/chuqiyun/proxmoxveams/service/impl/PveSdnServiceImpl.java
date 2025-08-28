package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dto.IpParams;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.dto.pvesdn.ZonesParams;
import com.chuqiyun.proxmoxveams.entity.*;
import com.chuqiyun.proxmoxveams.service.*;
import com.chuqiyun.proxmoxveams.utils.IpUtil;
import com.chuqiyun.proxmoxveams.utils.ModUtil;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import com.chuqiyun.proxmoxveams.utils.SdnUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author mryunqi
 * @date 2024/1/20
 */
@Service("pveSdnService")
public class PveSdnServiceImpl implements PveSdnService {
    @Resource
    private ZonesService zonesService;
    @Resource
    private MasterService masterService;
    @Resource
    private VnetsService vnetsService;
    @Resource
    private SubnetpoolService subnetpoolService;
    @Resource
    private SubnetService subnetService;

    /**
    * @Author: mryunqi
    * @Description: 添加区域
    * @DateTime: 2024/1/20 20:55
    * @Params: ZonesParams zonesParams 区域参数
    * @Return UnifiedResultDto<Object> 添加结果
    */
    @Override
    public UnifiedResultDto<Object> addZone(ZonesParams zonesParams) {
        if (zonesParams.getNodeId() == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null);
        }
        Master node = masterService.getById(zonesParams.getNodeId());
        if (ModUtil.isNull(node)) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NODE_NOT_EXIST, null);
        }
        // 判断节点是否可用
        if (node.getStatus() >= 1) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NODE_NOT_AVAILABLE, null);
        }
        // 判断参数是否为空
        if (zonesParams.getZone() == null || zonesParams.getType() == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null);
        }
        // 判断sdn区域类型是否合规
        if (!SdnUtil.isValidType(zonesParams.getType())) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null);
        }
        // 判断sdn区域是否存在
        if (zonesService.isZoneExist(zonesParams.getZone())) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_SDN_ZONE_NOT_EXIST, null);
        }
        Zones zones = new Zones();
        zones.setType(zonesParams.getType());
        zones.setZone(zonesParams.getZone());
        zones.setNodes(node.getNodeName());
        zones.setNodeId(node.getId());
        if (zonesParams.getIpam() == null){
            zones.setIpam(node.getNodeName());
        }else {
            zones.setIpam(zonesParams.getIpam());
        }
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
        HashMap<String, Object> params = new HashMap<>();
        params.put("zone", zones.getZone());
        params.put("type", zones.getType());
        params.put("nodes", zones.getNodes());
        params.put("ipam", zones.getIpam());
        JSONObject response;
        try{
            response = proxmoxApiUtil.postNodeApiForm(node, authentications, "/cluster/sdn/zones", params);
        }catch (Exception e){
            e.printStackTrace();
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_SDN_ZONE_ADD_FAILED, e);
        }
        if (response == null){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_SDN_ZONE_ADD_FAILED, null);
        }

        if (zonesService.addZone(zones)){

            return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, null);
        }
        return new UnifiedResultDto<>(UnifiedResultCode.ERROR_SDN_ZONE_ADD_FAILED, null);
    }

    /**
    * @Author: mryunqi
    * @Description: 根据id删除区域
    * @DateTime: 2024/1/21 16:40
    * @Params: Integer id 区域id
    * @Return  UnifiedResultDto<Object> 删除结果
    */
    @Override
    public UnifiedResultDto<Object> deleteZoneById(Integer id) {
        if (id == null){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null);
        }
        Zones zones = zonesService.getById(id);
        if (ModUtil.isNull(zones)){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_SDN_ZONE_NOT_EXIST, null);
        }
        Master node = masterService.getById(zones.getNodeId());
        if (ModUtil.isNull(node)){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NODE_NOT_EXIST, null);
        }
        // 判断节点是否可用
        if (node.getStatus() >= 1) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NODE_NOT_AVAILABLE, null);
        }
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
        JSONObject response;
        try{
            response = proxmoxApiUtil.deleteNodeApi(node, authentications, "/cluster/sdn/zones/" + zones.getZone(), null);
        }catch (Exception e){
            e.printStackTrace();
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_SDN_ZONE_DELETE_FAILED, e);
        }
        if (response == null){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_SDN_ZONE_DELETE_FAILED, null);
        }
        if (zonesService.deleteZone(zones)){
            return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, null);
        }
        return new UnifiedResultDto<>(UnifiedResultCode.ERROR_SDN_ZONE_DELETE_FAILED, null);
    }

    /**
    * @Author: mryunqi
    * @Description: 根据标识zone删除区域
    * @DateTime: 2024/1/21 16:55
    * @Params: String zone 区域标识
    * @Return UnifiedResultDto<Object> 删除结果
    */
    @Override
    public UnifiedResultDto<Object> deleteZoneByZone(String zone) {
        if (zone == null){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null);
        }
        Zones zones = zonesService.selectZoneByZone(zone);
        if (ModUtil.isNull(zones)){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_SDN_ZONE_NOT_EXIST, null);
        }
        Master node = masterService.getById(zones.getNodeId());
        if (ModUtil.isNull(node)){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NODE_NOT_EXIST, null);
        }
        // 判断节点是否可用
        if (node.getStatus() >= 1) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NODE_NOT_AVAILABLE, null);
        }
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
        JSONObject response;
        try{
            response = proxmoxApiUtil.deleteNodeApi(node, authentications, "/cluster/sdn/zones/" + zones.getZone(), null);
        }catch (Exception e){
            e.printStackTrace();
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_SDN_ZONE_DELETE_FAILED, e);
        }
        if (response == null){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_SDN_ZONE_DELETE_FAILED, null);
        }
        if (zonesService.deleteZone(zones)){
            return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, null);
        }
        return new UnifiedResultDto<>(UnifiedResultCode.ERROR_SDN_ZONE_DELETE_FAILED, null);
    }

    /**
    * @Author: mryunqi
    * @Description: 分页查询区域
    * @DateTime: 2024/1/21 17:06
    * @Params: Integer page 页码 Integer size 每页数量
    * @Return Page<Zones> 分页后的区域数据
    */
    @Override
    public Page<Zones> getZonesByPage(Integer page, Integer size) {
        return zonesService.selectZoneByPage(page, size);
    }

    /**
    * @Author: mryunqi
    * @Description: 添加vnet区域
    * @DateTime: 2024/1/24 21:59
    * @Params: Vnets vnets vnet区域信息
    * @Return UnifiedResultDto<Object> 添加结果
    */
    @Override
    public UnifiedResultDto<Object> addVnet(Vnets vnets) {
        if (vnets == null){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null);
        }
        if (vnets.getZone() == null){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null);
        }
        // 判断zone是否存在
        Zones zones = zonesService.selectZoneByZone(vnets.getZone());
        if (ModUtil.isNull(zones)){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_SDN_ZONE_NOT_EXIST, null);
        }
        Master node = masterService.getById(zones.getNodeId());
        if (ModUtil.isNull(node)){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NODE_NOT_EXIST, null);
        }
        // 判断节点是否可用
        if (node.getStatus() >= 1) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NODE_NOT_AVAILABLE, null);
        }
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
        JSONObject response;
        HashMap<String, Object> params = new HashMap<>();
        params.put("zone", vnets.getZone());
        params.put("vnet", vnets.getVnet());
        params.put("alias", vnets.getAlias());
        params.put("type", "vnet");
        if (vnets.getTag() != null){
            params.put("tag", vnets.getTag());
        }
        try{
            response = proxmoxApiUtil.postNodeApiForm(node, authentications, "/cluster/sdn/vnets", params);
        }catch (Exception e){
            e.printStackTrace();
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_SDN_VNET_ADD_FAILED, e);
        }
        if (response == null){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_SDN_VNET_ADD_FAILED, null);
        }
        if (vnetsService.addVnet(vnets)){
            return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, null);
        }
        return new UnifiedResultDto<>(UnifiedResultCode.ERROR_SDN_ZONE_ADD_FAILED, null);
    }

    /**
    * @Author: mryunqi
    * @Description: 分页查询vnet区域
    * @DateTime: 2024/1/24 23:17
    * @Params: Integer page 页码 Integer size 每页数量
    * @Return Page<Vnets> 分页后的vnet区域数据
    */
    @Override
    public Page<Vnets> getVnetsByPage(Integer page, Integer size) {
        return vnetsService.getVnetByPage(page, size);
    }
    
    /**
    * @Author: mryunqi
    * @Description: 新增subnet
    * @DateTime: 2024/1/26 15:02
    * @Params: Subnets subnets subnet对象
    * @Return UnifiedResultDto<Object> 新增结果
    */
    @Override
    public UnifiedResultDto<Object> addSubnet(Subnet subnet) {
        if (subnet == null || subnet.getSubnet() == null || subnet.getVnet() == null || subnet.getGateway() == null ||
        subnet.getMask() == null){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null);
        }
        // 判断vnet是否存在
        Vnets vnets = vnetsService.getVnetByName(subnet.getVnet());
        if (ModUtil.isNull(vnets)){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_SDN_VNET_NOT_EXIST, null);
        }
        // 查询zone
        Zones zones = zonesService.selectZoneByZone(vnets.getZone());
        Master node = masterService.getById(zones.getNodeId());
        if (ModUtil.isNull(node)){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NODE_NOT_EXIST, null);
        }
        // 判断节点是否可用
        if (node.getStatus() >= 1) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NODE_NOT_AVAILABLE, null);
        }
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
        JSONObject response;
        HashMap<String, Object> params = new HashMap<>();
        params.put("vnet", subnet.getVnet());
        params.put("subnet", subnet.getSubnet()+"/"+subnet.getMask());
        params.put("gateway", subnet.getGateway());
        params.put("type", "subnet");
        params.put("snat", subnet.getSnat());
        try{
            response = proxmoxApiUtil.postNodeApiForm(node, authentications, "/cluster/sdn/vnets/"+subnet.getVnet()+"/subnets", params);
        }catch (Exception e){
            e.printStackTrace();
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_SDN_SUBNET_ADD_FAILED, e);
        }
        if (response == null){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_SDN_SUBNET_ADD_FAILED, null);
        }
        if (subnetService.addSubnet(subnet)){
            IpParams ipParams = new IpParams();
            ipParams.setGateway(subnet.getGateway());
            ipParams.setMask(subnet.getMask());
            ipParams.setNodeId(node.getId());
            if (subnet.getDns() == null){
                ipParams.setDns1("114.114.114.114");
            }
            else {
                ipParams.setDns1(subnet.getDns());
            }
            ipParams.setSubnetId(subnet.getId());
            List<Subnetpool> subnetpools = IpUtil.getNatIpList(ipParams);
            // 批量插入natip
            if (subnetpoolService.addSubnetpools(subnetpools)){
                return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, null);
            }
            else {
                return new UnifiedResultDto<>(UnifiedResultCode.ERROR_SDN_SUBNET_ADD_FAILED, null);
            }
        }
        return new UnifiedResultDto<>(UnifiedResultCode.ERROR_SDN_SUBNET_ADD_FAILED, null);
    }


    /**
    * @Author: mryunqi
    * @Description: 根据vnet分页查询subnet
    * @DateTime: 2024/1/26 16:55
    * @Params: String vnet vnet名称 Integer page 页码 Integer size 每页数量
    * @Return Page<Subnet> 分页后的subnet数据
    */
    @Override
    public Page<Subnet> getSubnetsByVnet(String vnet, Integer page, Integer size) {
        return subnetService.getSubnetByVnetId(vnet, page, size);
    }
    
    /**
    * @Author: mryunqi
    * @Description: 应用sdn更改
    * @DateTime: 2024/1/26 17:21
    * @Params: nodeId 节点id
    * @Return UnifiedResultDto<Object> 应用结果
    */
    @Override
    public UnifiedResultDto<Object> applySdn(Integer nodeId) {
        Master node = masterService.getById(nodeId);
        if (ModUtil.isNull(node)){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NODE_NOT_EXIST, null);
        }
        // 判断节点是否可用
        if (node.getStatus() >= 1) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NODE_NOT_AVAILABLE, null);
        }
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
        JSONObject response;
        try{
            response = proxmoxApiUtil.putNodeApi(node, authentications, "/cluster/sdn", null);
        }catch (Exception e){
            e.printStackTrace();
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_SDN_APPLY_CONFIG_FAILED, e);
        }
        if (response == null){
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_SDN_APPLY_CONFIG_FAILED, null);
        }
        return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, null);
    }


}
