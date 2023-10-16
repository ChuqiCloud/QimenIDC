package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.PveNetworkService;
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
}
