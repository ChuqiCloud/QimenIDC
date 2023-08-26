package com.chuqiyun.proxmoxveams.service;

import java.util.HashMap;

/**
 * @author mryunqi
 * @date 2023/8/23
 */
public interface VmInfoService {
    HashMap<String, Object> getVmByPage(Integer page, Integer size);

    Object getVmHostPageByParam(Integer page, Integer size, String param, String value);
}
