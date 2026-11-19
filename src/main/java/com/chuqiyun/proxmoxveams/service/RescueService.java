package com.chuqiyun.proxmoxveams.service;

import com.chuqiyun.proxmoxveams.entity.RescueVmState;

import java.util.List;
import java.util.Map;

public interface RescueService {
    List<Map<String, Object>> listImages();

    RescueVmState enable(Integer hostId, String imageId, String storage);

    RescueVmState disable(Integer hostId, Boolean start);

    RescueVmState getState(Integer hostId);
}
