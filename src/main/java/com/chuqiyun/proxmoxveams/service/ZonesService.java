package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.Zones;

/**
 * (Zones)表服务接口
 *
 * @author mryunqi
 * @since 2024-01-20 17:48:40
 */
public interface ZonesService extends IService<Zones> {

    boolean addZone(Zones zones);

    boolean deleteZone(Zones zones);

    boolean updateZone(Zones zones);

    boolean isZoneExist(String zone);

    Zones selectZoneByZone(String zone);

    Page<Zones> selectZoneByPage(Integer page, Integer size);

    Page<Zones> selectZoneByPage(Integer page, Integer size, QueryWrapper<Zones> queryWrapper);
}

