package com.chuqiyun.proxmoxveams.service;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.VmParams;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * (Master)表服务接口
 *
 * @author mryunqi
 * @since 2023-06-10 01:18:17
 */
public interface MasterService extends IService<Master> {
    /**
     * @Author: mryunqi
     * @Description: 获取ProxmoxVE集群节点列表
     * @DateTime: 2023/6/19 20:16
     * @Params: Integer page 页数
     * @Params: Integer size 每页数据
     * @Return  Page<Master>
     */
    Page<Master> getMasterList(Integer page, Integer size);
    /**
     * @Author: mryunqi
     * @Description: 获取ProxmoxVE集群节点列表
     * @DateTime: 2023/6/19 20:16
     * @Params: Integer page 页数
     * @Params: Integer size 每页数据
     * @Params: QueryWrapper<Master> queryWrapper 查询条件
     * @Return  Page<Master>
     */
    Page<Master>getMasterList(Integer page, Integer size, QueryWrapper<Master> queryWrapper);
    /**
     * @Author: mryunqi
     * @Description: 获取ProxmoxVE集群节点的cookie
     * @DateTime: 2023/6/20 15:09
     * @Params: Integer id 节点id
     * @Return  HashMap<String,String>
     */
    HashMap<String,String> getMasterCookieMap(Integer id);
    /**
     * @Author: mryunqi
     * @Description: 生成最新vmid
     * @DateTime: 2023/6/21 15:21
     * @Params: Integer id 节点id
     * @Return Integer
     */
    Integer getNewVmid(Integer id);

    /**
     * @Author: mryunqi
     * @Description: 获取节点磁盘名称列表
     * @DateTime: 2023/6/21 20:07
     */
    ArrayList<JSONObject> getDiskList(Integer id);

    Integer createVm(VmParams vmParams);

    JSONObject getVmInfo(Integer nodeId, Integer vmid);

    JSONObject getVmStatusCurrent(Integer nodeId, Integer vmid);

    ArrayList<Integer> getAllNodeIdList();

    JSONObject getNodeVmInfoJsonList(Integer nodeId);

    void updateAllNodeCookie();

    void updateNodeCookie(Integer nodeId);

    Integer getVmStatusCode(Integer nodeId, Integer vmid);
}

