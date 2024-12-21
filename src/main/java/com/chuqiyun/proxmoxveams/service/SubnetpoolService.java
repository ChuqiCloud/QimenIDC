package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.Subnetpool;

import java.util.List;

/**
 * (Subnetpool)表服务接口
 *
 * @author mryunqi
 * @since 2024-01-26 13:53:07
 */
public interface SubnetpoolService extends IService<Subnetpool> {

    boolean addSubnetpools(List<Subnetpool> subnetpools);

    boolean deleteSubnetpoolsBySubnetId(Integer subnetId);

    List<Subnetpool> getSubnetpoolsBySubnetId(Integer subnetId);

    List<Subnetpool> getSubnetpoolsByVmid(String vmid);
}

