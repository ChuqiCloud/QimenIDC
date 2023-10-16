package com.chuqiyun.proxmoxveams.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * @author mryunqi
 * @date 2023/10/16
 */
public interface PveNetworkService {
    JSONArray getPveNetworkInfo(long nodeId);
}
