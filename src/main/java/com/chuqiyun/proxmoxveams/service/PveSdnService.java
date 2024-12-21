package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.dto.pvesdn.ZonesParams;
import com.chuqiyun.proxmoxveams.entity.Subnet;
import com.chuqiyun.proxmoxveams.entity.Vnets;
import com.chuqiyun.proxmoxveams.entity.Zones;

/**
 * @author mryunqi
 * @date 2024/1/20
 */
public interface PveSdnService {
    UnifiedResultDto<Object> addZone(ZonesParams zonesParams);

    UnifiedResultDto<Object> deleteZoneById(Integer id);

    UnifiedResultDto<Object> deleteZoneByZone(String zone);

    Page<Zones> getZonesByPage(Integer page, Integer size);

    UnifiedResultDto<Object> addVnet(Vnets vnets);

    Page<Vnets> getVnetsByPage(Integer page, Integer size);

    UnifiedResultDto<Object> addSubnet(Subnet subnet);

    Page<Subnet> getSubnetsByVnet(String vnet, Integer page, Integer size);

    UnifiedResultDto<Object> applySdn(Integer nodeId);
}
