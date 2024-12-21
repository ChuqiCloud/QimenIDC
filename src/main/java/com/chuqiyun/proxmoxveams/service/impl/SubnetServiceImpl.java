package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.SubnetDao;
import com.chuqiyun.proxmoxveams.entity.Subnet;
import com.chuqiyun.proxmoxveams.service.SubnetService;
import org.springframework.stereotype.Service;

/**
 * (Subnet)表服务实现类
 *
 * @author mryunqi
 * @since 2024-01-20 17:47:20
 */
@Service("subnetService")
public class SubnetServiceImpl extends ServiceImpl<SubnetDao, Subnet> implements SubnetService {

    /**
    * @Author: mryunqi
    * @Description: 添加子网
    * @DateTime: 2024/1/26 13:30
    * @Params: Subnet subnet 子网对象
    * @Return boolean 是否添加成功
    */
    @Override
    public boolean addSubnet(Subnet subnet) {
        return this.save(subnet);
    }

    /**
    * @Author: mryunqi
    * @Description: 根据id删除子网
    * @DateTime: 2024/1/26 13:33
    * @Params: Integer id 子网id
    * @Return boolean 是否删除成功
    */
    @Override
    public boolean deleteSubnetById(Integer id) {
        return this.removeById(id);
    }

    /**
    * @Author: mryunqi
    * @Description: 修改子网
    * @DateTime: 2024/1/26 13:34
    * @Params: Subnet subnet 子网对象
    * @Return boolean 是否修改成功
    */
    @Override
    public boolean updateSubnet(Subnet subnet) {
        return this.updateById(subnet);
    }

    /**
    * @Author: mryunqi
    * @Description: 根据vnet名称分页查询子网
    * @DateTime: 2024/1/26 13:35
    * @Params: String vnet vnet 名称 Integer page 页码 Integer size 每页条数
    * @Return Page<Subnet> 子网分页对象
    */
    @Override
    public Page<Subnet> getSubnetByVnetId(String vnet, Integer page, Integer size) {
        Page<Subnet> subnetPage = new Page<>(page, size);
        QueryWrapper<Subnet> subnetQueryWrapper = new QueryWrapper<>();
        subnetQueryWrapper.eq("vnet", vnet);
        return this.page(subnetPage, subnetQueryWrapper);
    }
}

