package com.chuqiyun.proxmoxveams.service;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.dto.VmHostDto;

import java.util.HashMap;

/**
 * @author mryunqi
 * @date 2023/8/23
 */
public interface VmInfoService {
    HashMap<String, Object> getVmByPage(Integer page, Integer size);

    HashMap<String, Object> getVmByPageOrderByCreateTime(Integer page, Integer size);

    Long getVmCount();

    Object getVmHostPageByParam(Integer page, Integer size, String param, String value);

    VmHostDto getVmHostById(Integer vmId);

    JSONObject getVmInfoRrdData(Integer vmId, String timeframe, String cf);
}
