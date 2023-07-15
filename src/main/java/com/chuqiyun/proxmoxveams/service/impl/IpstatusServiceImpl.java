package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.IpstatusDao;
import com.chuqiyun.proxmoxveams.entity.IpParams;
import com.chuqiyun.proxmoxveams.entity.Ipstatus;
import com.chuqiyun.proxmoxveams.service.IpstatusService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * (Ipstatus)表服务实现类
 *
 * @author mryunqi
 * @since 2023-07-02 23:16:38
 */
@Service("ipstatusService")
public class IpstatusServiceImpl extends ServiceImpl<IpstatusDao, Ipstatus> implements IpstatusService {
    /**
    * @Author: mryunqi
    * @Description: 插入IP组信息
    * @DateTime: 2023/7/2 23:20
    * @Params: IpParams ipParams
    * @Return Integer
    */
    @Override
    public Integer insertIpstatus(IpParams ipParams) {
        Ipstatus ipstatus = new Ipstatus();
        ipstatus.setName(ipParams.getPoolName());
        ipstatus.setGateway(ipParams.getGateway());
        ipstatus.setMask(ipParams.getMask());
        ipstatus.setDns1(ipParams.getDns1());
        ipstatus.setDns2(ipParams.getDns2());
        ipstatus.setNodeid(ipParams.getNodeId());
        return this.save(ipstatus) ? ipstatus.getId() : null;
    }

    /**
    * @Author: mryunqi
    * @Description: 分页获取IP组信息
    * @DateTime: 2023/7/4 14:15
    * @Params: Integer page, Integer limit
    * @Return Page<Ipstatus>
    */
    @Override
    public Page<Ipstatus> getIpstatusPage(Integer page, Integer limit) {
        Page<Ipstatus> ipstatusPage = new Page<>(page,limit);
        return this.page(ipstatusPage);
    }

    /**
    * @Author: mryunqi
    * @Description: 分页获取IP组信息附加条件
    * @DateTime: 2023/7/4 14:16
    * @Params: Integer page, Integer limit, QueryWrapper<Ipstatus> queryWrapper
    * @Return Page<Ipstatus>
    */
    @Override
    public Page<Ipstatus> getIpstatusPage(Integer page, Integer limit, QueryWrapper<Ipstatus> queryWrapper){
        Page<Ipstatus> ipstatusPage = new Page<>(page,limit);
        return this.page(ipstatusPage,queryWrapper);
    }
    /**
    * @Author: mryunqi
    * @Description: 修改IP池信息
    * @DateTime: 2023/7/4 16:37
    * @Params: Ipstatus ipstatus
    * @Return boolean
    */
    @Override
    public boolean updateIpStatus(Ipstatus ipstatus) {
        return this.updateById(ipstatus);
    }
    /**
    * @Author: mryunqi
    * @Description: 获取所有ID
    * @DateTime: 2023/7/4 21:41
    */
    @Override
    public List<Integer> getAllId() {
        return this.lambdaQuery().select(Ipstatus::getId).list().stream().map(Ipstatus::getId).collect(Collectors.toList());
    }
    /**
    * @Author: mryunqi
    * @Description: 获取指定nodeID下available最大的IP组
    * @DateTime: 2023/7/6 18:24
    * @Params: Integer nodeId
    * @Return Ipstatus
    */
    @Override
    public Ipstatus getIpStatusMaxByNodeId(Integer nodeId) {
        return this.lambdaQuery().eq(Ipstatus::getNodeid,nodeId).orderByDesc(Ipstatus::getAvailable).last("limit 1").one();
    }

}

