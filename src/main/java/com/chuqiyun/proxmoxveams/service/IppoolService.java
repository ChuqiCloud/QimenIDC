package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.Ippool;

import java.util.List;

/**
 * (Ippool)表服务接口
 *
 * @author mryunqi
 * @since 2023-07-02 19:08:38
 */
public interface IppoolService extends IService<Ippool> {

    boolean insertIppoolList(List<Ippool> ippoolList);

    boolean isGatewayInIppool(String gateway);

    List<Ippool> getIppoolListByGateway(String gateway);

    List<String> getIpListByGateway(String gateway);

    List<String> getIpListByPoolId(Integer ippoolId);

    List<Ippool> getIppoolListByPoolId(Integer ippoolId);

    Page<Ippool> getIppoolListByPoolId(Integer ippoolId, Integer page, Integer limit);

    boolean updateIppoolList(List<Ippool> ippoolList);

    List<Integer> getAllIdList();

    Integer getIpCountByCondition(QueryWrapper<Ippool> ippool);

    Long getIpCountByPoolId(Integer ippoolId);

    Ippool getOneOkIpByPoolId(Integer ippoolId);

    Ippool getIppoolByIp(String ip);
}

