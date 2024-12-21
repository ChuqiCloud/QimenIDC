package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.Subnet;

/**
 * (Subnet)表服务接口
 *
 * @author mryunqi
 * @since 2024-01-20 17:47:20
 */
public interface SubnetService extends IService<Subnet> {

    boolean addSubnet(Subnet subnet);

    boolean deleteSubnetById(Integer id);

    boolean updateSubnet(Subnet subnet);

    Page<Subnet> getSubnetByVnetId(String vnet, Integer page, Integer size);
}

