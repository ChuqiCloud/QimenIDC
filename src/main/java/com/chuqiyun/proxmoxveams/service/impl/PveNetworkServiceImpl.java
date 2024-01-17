package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dto.NetWorkParams;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.service.ConfigService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.PveNetworkService;
import com.chuqiyun.proxmoxveams.utils.ClientApiUtil;
import com.chuqiyun.proxmoxveams.utils.ModUtil;
import com.chuqiyun.proxmoxveams.utils.NetWorkUtil;
import com.chuqiyun.proxmoxveams.utils.ProxmoxApiUtil;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;

/**
 * @author mryunqi
 * @date 2023/10/16
 */
@Service("pveNetworkService")
public class PveNetworkServiceImpl implements PveNetworkService {
    @Resource
    private MasterService masterService;
    @Resource
    private ConfigService configService;
    /**
    * @Author: mryunqi
    * @Description: 获取指定节点的网络信息
    * @DateTime: 2023/10/16 20:44
    * @Params: long nodeId 节点id
    * @Return JSONObject 原始数据
    */
    @Override
    public JSONArray getPveNetworkInfo(long nodeId) {
        // 获取节点信息
        Master node = masterService.getById(nodeId);
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
        HashMap<String, Object> params = new HashMap<>();
        JSONObject response = null;
        try{
            response = proxmoxApiUtil.getNodeApi(node, authentications, "/nodes/" + node.getNodeName() + "/network", params);
        }catch (Exception e){
            e.printStackTrace();
        }
        assert response != null;
        return response.getJSONArray("data");
    }

    /**
    * @Author: mryunqi
    * @Description: 获取节点网卡配置文件信息
    * @DateTime: 2023/10/28 22:03
    * @Params: long nodeId 节点id
    * @Return String 网卡配置文件信息
    */
    @Override
    public String getPveInterfaces(long nodeId){
        // 获取被控通讯token
        String token = configService.getToken();
        // 获取节点信息
        Master node = masterService.getById(nodeId);

        return ClientApiUtil.getNetworkInfo(node.getHost(),node.getControllerPort(), token).getJSONObject("data").getString("data");
    }

    /**
    * @Author: mryunqi
    * @Description: 创建虚拟网卡
    * @DateTime: 2024/1/17 21:17
    * @Params: long nodeId 节点id NetWorkParams 网卡参数
    * @Return UnifiedResultDto<Object> 创建结果
    */
    @Override
    public UnifiedResultDto<Object> createNetWork(long nodeId, NetWorkParams netWorkParams){
        if (nodeId <= 0) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null);
        }
        Master node = masterService.getById(nodeId);
        if (ModUtil.isNull(node)) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NODE_NOT_EXIST, null);
        }
        // 判断节点是否可用
        if (node.getStatus() >= 1) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NODE_NOT_AVAILABLE, null);
        }
        if (netWorkParams.getNode() == null) {
            netWorkParams.setNode(node.getNodeName());
        }
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        HashMap<String, String> authentications = masterService.getMasterCookieMap(node.getId());
        HashMap<String, Object> params = NetWorkUtil.getNetWorkParamsMap(netWorkParams);
        JSONObject response = null;
        try{
            response = proxmoxApiUtil.postNodeApiForm(node, authentications, "/nodes/" + node.getNodeName() + "/network", params);
        }catch (Exception e){
            e.printStackTrace();
        }
        return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, response);
    }
}
