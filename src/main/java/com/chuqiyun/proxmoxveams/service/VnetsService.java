package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.Vnets;

/**
 * (Vnets)表服务接口
 *
 * @author mryunqi
 * @since 2024-01-20 17:47:41
 */
public interface VnetsService extends IService<Vnets> {

    boolean addVnet(Vnets vnets);

    boolean deleteVnetById(Integer id);

    boolean deleteVnetByName(String vnet);

    boolean updateVnet(Vnets vnets);

    Vnets getVnetById(Integer id);

    Vnets getVnetByName(String vnet);

    Page<Vnets> getVnetByPage(Integer page, Integer size);
}

