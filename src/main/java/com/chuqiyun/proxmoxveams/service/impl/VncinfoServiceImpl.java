package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.VncinfoDao;
import com.chuqiyun.proxmoxveams.entity.Vncinfo;
import com.chuqiyun.proxmoxveams.service.VncinfoService;
import org.springframework.stereotype.Service;

/**
 * (Vncinfo)表服务实现类
 *
 * @author mryunqi
 * @since 2023-11-22 13:42:50
 */
@Service("vncinfoService")
public class VncinfoServiceImpl extends ServiceImpl<VncinfoDao, Vncinfo> implements VncinfoService {
    /**
     * @Author: mryunqi
     * @Description: 添加虚拟机VNC配置信息
     * @DateTime: 2023/11/24 15:54
     * @Params: Vncinfo vncinfo 虚拟机VNC配置信息实体类
     * @Return  boolean
     */
    @Override
    public boolean addVncinfo(Vncinfo vncinfo) {
        return this.save(vncinfo);
    }

    /**
    * @Author: mryunqi
    * @Description: 删除虚拟机VNC配置信息
    * @DateTime: 2023/11/24 15:56
    * @Params: Long id 虚拟机VNC配置信息ID
    * @Return boolean
    */
    @Override
    public boolean deleteVncinfo(Long id) {
        return this.removeById(id);
    }

    /**
    * @Author: mryunqi
    * @Description: 修改虚拟机VNC配置信息
    * @DateTime: 2023/11/24 15:57
    * @Params: Vncinfo vncinfo 虚拟机VNC配置信息实体类
    * @Return boolean
    */
    @Override
    public boolean updateVncinfo(Vncinfo vncinfo) {
        return this.updateById(vncinfo);
    }

    /**
    * @Author: mryunqi
    * @Description: 分页查询虚拟机VNC配置信息
    * @DateTime: 2023/11/24 15:58
    * @Params: Integer page 当前页码 Integer limit 每页条数
    * @Return
    */
    @Override
    public Page<Vncinfo> selectVncinfoPage(Integer page, Integer limit) {
        Page<Vncinfo> page1 = new Page<>(page, limit);
        return this.page(page1);
    }

    /**
    * @Author: mryunqi
    * @Description: 带参数分页查询虚拟机VNC配置信息
    * @DateTime: 2023/11/24 15:59
    * @Params: Integer page 当前页码 Integer limit 每页条数 QueryWrapper<Vncinfo> queryWrapper 条件构造器
    * @Return  Page<Vncinfo> 分页数据
    */
    @Override
    public Page<Vncinfo> selectVncinfoPage(Integer page, Integer limit, QueryWrapper<Vncinfo> queryWrapper) {
        Page<Vncinfo> page1 = new Page<>(page, limit);
        return this.page(page1, queryWrapper);
    }

    /**
    * @Author: mryunqi
    * @Description: 根据虚拟机id查询虚拟机VNC配置信息
    * @DateTime: 2023/11/24 17:15
    * @Params: Long hostId 虚拟机id
    * @Return Vncinfo
    */
    @Override
    public Vncinfo selectVncinfoByHostId(Long hostId) {
        QueryWrapper<Vncinfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("host_id", hostId);
        return this.getOne(queryWrapper);
    }

}

