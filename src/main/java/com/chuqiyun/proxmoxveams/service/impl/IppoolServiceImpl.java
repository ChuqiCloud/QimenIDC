package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.IppoolDao;
import com.chuqiyun.proxmoxveams.entity.Ippool;
import com.chuqiyun.proxmoxveams.service.IppoolService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * (Ippool)表服务实现类
 *
 * @author mryunqi
 * @since 2023-07-02 19:08:38
 */
@Service("ippoolService")
public class IppoolServiceImpl extends ServiceImpl<IppoolDao, Ippool> implements IppoolService {
    /**
    * @Author: mryunqi
    * @Description: 批量插入ip池
    * @DateTime: 2023/7/2 22:05
    * @Params: List<Ippool> ippoolList
    * @Return boolean
    */
    @Override
    public boolean insertIppoolList(List<Ippool> ippoolList) {
        return this.saveBatch(ippoolList,254);
    }

    /**
    * @Author: mryunqi
    * @Description: 判断网关是否在ip池中
    * @DateTime: 2023/7/2 23:00
    * @Params: String gateway
    * @Return boolean
    */
    @Override
    public boolean isGatewayInIppool(String gateway) {
        return this.lambdaQuery().eq(Ippool::getGateway,gateway).count() > 0;
    }

    /**
    * @Author: mryunqi
    * @Description: 获取指定网关IP实体类列表
    * @DateTime: 2023/7/3 22:50
    * @Params: String gateway
    * @Return  List<Ippool>
    */
    @Override
    public List<Ippool> getIppoolListByGateway(String gateway) {
        return this.lambdaQuery().eq(Ippool::getGateway,gateway).list();
    }
    /**
    * @Author: mryunqi
    * @Description: 获取指定网关IP列表
    * @DateTime: 2023/7/3 22:52
    * @Params: String gateway
    * @Return List<String
    */
    @Override
    public List<String> getIpListByGateway(String gateway) {
        return this.lambdaQuery().eq(Ippool::getGateway,gateway).list().stream().map(Ippool::getIp).collect(Collectors.toList());
    }
    /**
    * @Author: mryunqi
    * @Description: 获取指定IP池ID IP列表
    * @DateTime: 2023/7/3 22:55
    * @Params: Integer ippoolId
    * @Return List<String>
    */
    @Override
    public List<String> getIpListByPoolId(Integer ippoolId) {
        return this.lambdaQuery().eq(Ippool::getPoolId,ippoolId).list().stream().map(Ippool::getIp).collect(Collectors.toList());
    }
    /**
    * @Author: mryunqi
    * @Description: 获取指定IP池ID IP实体类列表
    * @DateTime: 2023/7/3 22:56
    * @Params: Integer ippoolId
    * @Return List<Ippool>
    */
    @Override
    public List<Ippool> getIppoolListByPoolId(Integer ippoolId) {
        return this.lambdaQuery().eq(Ippool::getPoolId,ippoolId).list();
    }
    /**
    * @Author: mryunqi
    * @Description: 获取指定IP池ID IP实体类分页列表
    * @DateTime: 2023/7/4 16:22
    * @Params: Integer ippoolId, Integer page, Integer limit
    * @Return Page<Ippool>
    */
    @Override
    public Page<Ippool> getIppoolListByPoolId(Integer ippoolId, Integer page, Integer limit) {
        return this.lambdaQuery().eq(Ippool::getPoolId,ippoolId).page(new Page<>(page,limit));
    }
    /**
    * @Author: mryunqi
    * @Description: 批量更新ip池
    * @DateTime: 2023/7/4 16:46
    * @Params: List<Ippool> ippoolList
    * @Return boolean
    */
    @Override
    public boolean updateIppoolList(List<Ippool> ippoolList) {
        return this.updateBatchById(ippoolList,254);
    }
    /**
    * @Author: mryunqi
    * @Description: 获取所有ID列表
    * @DateTime: 2023/7/4 17:23
    */
    @Override
    public List<Integer> getAllIdList() {
        return this.lambdaQuery().select(Ippool::getId).list().stream().map(Ippool::getId).collect(Collectors.toList());
    }
    /**
    * @Author: mryunqi
    * @Description: 获取指定条件IP数量
    * @DateTime: 2023/7/4 17:28
    * @Params: QueryWrapper<Ippool> ippool
    * @Return Integer
    */
    @Override
    public Integer getIpCountByCondition(QueryWrapper<Ippool> ippool) {
        List<Ippool> ippoolList = this.list(ippool);
        return ippoolList.size();
    }
    /**
    * @Author: mryunqi
    * @Description: 获取指定IP池ID IP数量
    * @DateTime: 2023/7/4 17:32
    * @Params:  Integer ippoolId
    * @Return Long
    */
    @Override
    public Long getIpCountByPoolId(Integer ippoolId) {
        return this.lambdaQuery().eq(Ippool::getPoolId,ippoolId).count();
    }
    /**
    * @Author: mryunqi
    * @Description: 获取指定ID可用IP其中一个
    * @DateTime: 2023/7/6 18:40
    * @Params: Integer ippoolId
    * @Return String
    */
    @Override
    public Ippool getOneOkIpByPoolId(Integer ippoolId) {
        return this.lambdaQuery().eq(Ippool::getPoolId,ippoolId).eq(Ippool::getStatus,0).last("limit 1").one();
    }

}

