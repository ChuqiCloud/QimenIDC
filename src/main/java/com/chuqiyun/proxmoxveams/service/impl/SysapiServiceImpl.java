package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.SysapiDao;
import com.chuqiyun.proxmoxveams.entity.Sysapi;
import com.chuqiyun.proxmoxveams.service.SysapiService;
import org.springframework.stereotype.Service;

/**
 * (Sysapi)表服务实现类
 *
 * @author mryunqi
 * @since 2023-06-10 19:11:15
 */
@Service("sysapiService")
public class SysapiServiceImpl extends ServiceImpl<SysapiDao, Sysapi> implements SysapiService {
    /**
     * @Author: mryunqi
     * @Description: 获取单个api的信息
     * @DateTime: 2023/5/8 16:56
     * @Params: String appId
     * @Return Sysapi
     */
    @Override
    public Sysapi getSysapi(String appId){
        QueryWrapper<Sysapi> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("appid",appId);
        return this.getOne(queryWrapper);
    }
    /**
    * @Author: mryunqi
    * @Description: 分页获取api信息
    * @DateTime: 2023/7/24 22:11
    * @Params: int page 页码 int limit 每页数量
    * @Return Page<Sysapi> 分页信息
    */
    @Override
    public Page<Sysapi> selectSysapiPage(int page, int limit) {
        Page<Sysapi> sysapiPage = new Page<>(page,limit);
        return this.page(sysapiPage);
    }

    /**
    * @Author: mryunqi
    * @Description: 删除指定id的api
    * @DateTime: 2023/7/24 22:26
    * @Params: Integer id api的id
    * @Return  boolean
    */
    @Override
    public boolean deleteSysapiById(Integer id) {
        QueryWrapper<Sysapi> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id",id);
        return this.remove(queryWrapper);
    }
}

