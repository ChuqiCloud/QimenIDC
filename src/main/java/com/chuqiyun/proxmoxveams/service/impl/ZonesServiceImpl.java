package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.ZonesDao;
import com.chuqiyun.proxmoxveams.entity.Zones;
import com.chuqiyun.proxmoxveams.service.ZonesService;
import org.springframework.stereotype.Service;

/**
 * (Zones)表服务实现类
 *
 * @author mryunqi
 * @since 2024-01-20 17:48:40
 */
@Service("zonesService")
public class ZonesServiceImpl extends ServiceImpl<ZonesDao, Zones> implements ZonesService {

    /**
    * @Author: mryunqi
    * @Description: 新增区域
    * @DateTime: 2024/1/20 20:45
    * @Params: Zones zones 区域实体
    * @Return  boolean 是否新增成功
    */
    @Override
    public boolean addZone(Zones zones) {
        return this.save(zones);
    }

    /**
    * @Author: mryunqi
    * @Description: 删除区域
    * @DateTime: 2024/1/20 20:47
    * @Params: Zones zones 区域实体
    * @Return boolean 是否删除成功
    */
    @Override
    public boolean deleteZone(Zones zones) {
        return this.removeById(zones.getId());
    }

    /**
    * @Author: mryunqi
    * @Description: 修改区域
    * @DateTime: 2024/1/20 20:48
    * @Params: Zones zones 区域实体
    * @Return boolean 是否修改成功
    */
    @Override
    public boolean updateZone(Zones zones) {
        return this.updateById(zones);
    }

    /**
    * @Author: mryunqi
    * @Description: 判断区域是否存在
    * @DateTime: 2024/1/21 17:29
    * @Params: String zone 区域标识符
    * @Return boolean 是否存在
    */
    @Override
    public boolean isZoneExist(String zone) {
        QueryWrapper<Zones> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("zone",zone);
        return this.count(queryWrapper) > 0;
    }

    /**
    * @Author: mryunqi
    * @Description: 根据标识符zone查询区域
    * @DateTime: 2024/1/21 16:56
    * @Params: String zone 区域标识符
    * @Return Zones 区域实体
    */
    @Override
    public Zones selectZoneByZone(String zone) {
        QueryWrapper<Zones> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("zone",zone);
        return this.getOne(queryWrapper);
    }

    /**
    * @Author: mryunqi
    * @Description: 分页查询区域
    * @DateTime: 2024/1/20 20:48
    * @Params: Integer page 当前页 Integer size 每页显示条数
    * @Return  Page<Zones> 分页后的区域数据
    */
    @Override
    public Page<Zones> selectZoneByPage(Integer page, Integer size) {
        Page<Zones> zonesPage = new Page<>(page,size);
        return this.page(zonesPage);
    }

    /**
    * @Author: mryunqi
    * @Description: 带参数分页查询区域
    * @DateTime: 2024/1/20 20:50
    * @Params: Integer page 当前页 Integer size 每页显示条数 QueryWrapper<Zones> queryWrapper 查询条件
    * @Return Page<Zones> 分页后的区域数据
    */
    @Override
    public Page<Zones> selectZoneByPage(Integer page, Integer size, QueryWrapper<Zones> queryWrapper) {
        Page<Zones> zonesPage = new Page<>(page,size);
        return this.page(zonesPage,queryWrapper);
    }

}

