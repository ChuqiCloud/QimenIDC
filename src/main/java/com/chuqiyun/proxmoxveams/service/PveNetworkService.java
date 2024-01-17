package com.chuqiyun.proxmoxveams.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.dto.NetWorkParams;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;

/**
 * @author mryunqi
 * @date 2023/10/16
 */
public interface PveNetworkService {
    JSONArray getPveNetworkInfo(long nodeId);

    String getPveInterfaces(long nodeId);

    UnifiedResultDto<Object> createNetWork(long nodeId, NetWorkParams netWorkParams);
}
