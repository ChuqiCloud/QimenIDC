package com.chuqiyun.proxmoxveams.service;

import com.chuqiyun.proxmoxveams.entity.Vmhost;

/**
 * @author mryunqi
 * @date 2023/12/3
 */
public interface FlowService {
    Boolean insertFlowdata(Integer hostId);

    Boolean syncVmFlowdata(Integer hostId, Vmhost vmhost);
}
