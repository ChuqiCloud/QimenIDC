package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.SubnetpoolDao;
import com.chuqiyun.proxmoxveams.entity.Subnetpool;
import com.chuqiyun.proxmoxveams.service.SubnetpoolService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * (Subnetpool)表服务实现类
 *
 * @author mryunqi
 * @since 2024-01-26 13:53:07
 */
@Service("subnetpoolService")
public class SubnetpoolServiceImpl extends ServiceImpl<SubnetpoolDao, Subnetpool> implements SubnetpoolService {
    /**
    * @Author: mryunqi
    * @Description: 批量插入ip池
    * @DateTime: 2024/1/26 14:10
    * @Params: List<Subnetpool> subnetpools ip池对象集合
    * @Return boolean 是否插入成功
    */
    @Override
    public boolean addSubnetpools(List<Subnetpool> subnetpools) {
        return this.saveBatch(subnetpools);
    }

    /**
    * @Author: mryunqi
    * @Description: 删除指定subnet id的ip池
    * @DateTime: 2024/1/26 14:23
    * @Params: Integer subnetId 子网id
    * @Return boolean 是否删除成功
    */
    @Override
    public boolean deleteSubnetpoolsBySubnetId(Integer subnetId) {
        return this.removeById(subnetId);
    }

    /**
    * @Author: mryunqi
    * @Description: 根据subnat id查询ip列表
    * @DateTime: 2024/1/26 14:43
    * @Params: Integer subnetId 子网id
    * @Return List<Subnetpool> ip池对象集合
    */
    @Override
    public List<Subnetpool> getSubnetpoolsBySubnetId(Integer subnetId) {
        return this.lambdaQuery().eq(Subnetpool::getSubnatId,subnetId).list();
    }

    /**
    * @Author: mryunqi
    * @Description: 根据指定vmid 查询ip列表
    * @DateTime: 2024/1/26 14:57
    * @Params: String vmid 虚拟机id
    * @Return List<Subnetpool> ip池对象集合
    */
    @Override
    public List<Subnetpool> getSubnetpoolsByVmid(String vmid) {
        return this.lambdaQuery().eq(Subnetpool::getVmId,vmid).list();
    }

}

