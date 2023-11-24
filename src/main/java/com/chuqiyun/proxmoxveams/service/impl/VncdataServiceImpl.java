package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.VncdataDao;
import com.chuqiyun.proxmoxveams.entity.Vncdata;
import com.chuqiyun.proxmoxveams.service.VncdataService;
import org.springframework.stereotype.Service;

/**
 * (Vncdata)表服务实现类
 *
 * @author mryunqi
 * @since 2023-11-22 13:43:08
 */
@Service("vncdataService")
public class VncdataServiceImpl extends ServiceImpl<VncdataDao, Vncdata> implements VncdataService {

    /**
    * @Author: mryunqi
    * @Description: 添加虚拟机VNC连接信息
    * @DateTime: 2023/11/24 16:00
    * @Params: Vncdata vncdata 虚拟机VNC连接信息实体类
    * @Return boolean
    */
    @Override
    public boolean addVncdata(Vncdata vncdata) {
        return this.save(vncdata);
    }

    /**
    * @Author: mryunqi
    * @Description: 删除虚拟机VNC连接信息
    * @DateTime: 2023/11/24 16:01
    * @Params: Long id 虚拟机VNC连接信息ID
    * @Return boolean
    */
    @Override
    public boolean deleteVncdata(Long id) {
        return this.removeById(id);
    }

    /**
    * @Author: mryunqi
    * @Description: 修改虚拟机VNC连接信息
    * @DateTime: 2023/11/24 16:01
    * @Params: Vncdata vncdata 虚拟机VNC连接信息实体类
    * @Return boolean
    */
    @Override
    public boolean updateVncdata(Vncdata vncdata) {
        return this.updateById(vncdata);
    }

    /**
    * @Author: mryunqi
    * @Description: 分页查询虚拟机VNC连接信息
    * @DateTime: 2023/11/24 16:01
    * @Params: Integer page 当前页 Integer limit 每页条数
    * @Return Page<Vncdata> 虚拟机VNC连接信息分页数据
    */
    @Override
    public Page<Vncdata> selectVncdataPage(Integer page, Integer limit) {
        Page<Vncdata> page1 = new Page<>(page, limit);
        return this.page(page1);
    }

    /**
    * @Author: mryunqi
    * @Description: 带条件分页查询虚拟机VNC连接信息
    * @DateTime: 2023/11/24 16:03
    * @Params: Integer page 当前页 Integer limit 每页条数 QueryWrapper<Vncdata> wrapper 条件构造器
    * @Return Page<Vncdata> 虚拟机VNC连接信息分页数据
    */
    @Override
    public Page<Vncdata> selectVncdataPage(Integer page, Integer limit, QueryWrapper<Vncdata> queryWrapper) {
        Page<Vncdata> page1 = new Page<>(page, limit);
        return this.page(page1, queryWrapper);
    }

    /**
    * @Author: mryunqi
    * @Description: 根据VNC配置信息ID查询VNC连接信息
    * @DateTime: 2023/11/24 18:55
    * @Params: Long vncinfoId VNC配置信息ID
    * @Return Vncdata 虚拟机VNC连接信息实体类
    */
    @Override
    public Vncdata selectVncdataByVncinfoId(Long vncinfoId) {
        QueryWrapper<Vncdata> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("vnc_id", vncinfoId);
        return this.getOne(queryWrapper);
    }

    /**
    * @Author: mryunqi
    * @Description: 根据VNC配置信息ID查询VNC连接信息,status为0
    * @DateTime: 2023/11/24 19:47
    * @Params: Long vncinfoId VNC配置信息ID
    * @Return  Vncdata 虚拟机VNC连接信息实体类
    */
    @Override
    public Vncdata selectVncdataByVncinfoIdAndStatusOk(Long vncinfoId) {
        QueryWrapper<Vncdata> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("vnc_id", vncinfoId);
        queryWrapper.eq("status", 0);
        return this.getOne(queryWrapper);
    }

}

