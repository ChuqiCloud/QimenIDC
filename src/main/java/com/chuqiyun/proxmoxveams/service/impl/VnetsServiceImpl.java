package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.VnetsDao;
import com.chuqiyun.proxmoxveams.entity.Vnets;
import com.chuqiyun.proxmoxveams.service.VnetsService;
import org.springframework.stereotype.Service;

/**
 * (Vnets)表服务实现类
 *
 * @author mryunqi
 * @since 2024-01-20 17:47:41
 */
@Service("vnetsService")
public class VnetsServiceImpl extends ServiceImpl<VnetsDao, Vnets> implements VnetsService {

    /**
    * @Author: mryunqi
    * @Description: 新增vnet区域
    * @DateTime: 2024/1/24 20:39
    * @Params:  Vnets vnets vnet区域实体类
    * @Return  boolean 是否新增成功
    */
    @Override
    public boolean addVnet(Vnets vnets) {
        return this.save(vnets);
    }

    /**
    * @Author: mryunqi
    * @Description: 根据id删除vnet区域
    * @DateTime: 2024/1/24 20:41
    * @Params: Integer id vnet区域id
    * @Return boolean 是否删除成功
    */
    @Override
    public boolean deleteVnetById(Integer id) {
        return this.removeById(id);
    }

    /**
    * @Author: mryunqi
    * @Description: 根据vnet名称删除vnet区域
    * @DateTime: 2024/1/24 20:43
    * @Params: String vnet vnet区域名称
    * @Return boolean 是否删除成功
    */
    @Override
    public boolean deleteVnetByName(String vnet) {
        QueryWrapper<Vnets> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("vnet",vnet);
        return this.remove(queryWrapper);
    }

    /**
    * @Author: mryunqi
    * @Description: 修改vnet区域
    * @DateTime: 2024/1/24 20:44
    * @Params: Vnets vnets vnet区域实体类
    * @Return boolean 是否修改成功
    */
    @Override
    public boolean updateVnet(Vnets vnets) {
        return this.updateById(vnets);
    }

    /**
    * @Author: mryunqi
    * @Description: 根据id查询vnet区域
    * @DateTime: 2024/1/24 20:45
    * @Params: Integer id vnet区域id
    * @Return Vnets vnet区域实体类
    */
    @Override
    public Vnets getVnetById(Integer id) {
        return this.getById(id);
    }

    /**
    * @Author: mryunqi
    * @Description: 根据vnet名称查询vnet区域
    * @DateTime: 2024/1/24 20:45
    * @Params: String vnet vnet区域名称
    * @Return Vnets vnet区域实体类
    */
    @Override
    public Vnets getVnetByName(String vnet) {
        QueryWrapper<Vnets> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("vnet",vnet);
        return this.getOne(queryWrapper);
    }
    
    /**
    * @Author: mryunqi
    * @Description: 分页查询vnet区域
    * @DateTime: 2024/1/24 23:10
    * @Params: Integer page 当前页数 Integer size 每页显示条数
    * @Return Page<Vnets> vnet区域分页实体类
    */
    @Override
    public Page<Vnets> getVnetByPage(Integer page, Integer size) {
        Page<Vnets> vnetsPage = new Page<>(page,size);
        return this.page(vnetsPage);
    }


}

