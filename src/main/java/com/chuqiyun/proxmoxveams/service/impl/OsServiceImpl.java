package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.OsDao;
import com.chuqiyun.proxmoxveams.entity.Os;
import com.chuqiyun.proxmoxveams.service.OsService;
import org.springframework.stereotype.Service;

/**
 * (Os)表服务实现类
 *
 * @author mryunqi
 * @since 2023-07-08 15:58:22
 */
@Service("osService")
public class OsServiceImpl extends ServiceImpl<OsDao, Os> implements OsService {
    /**
    * @Author: mryunqi
    * @Description: 查询指定名称os是否存在
    * @DateTime: 2023/7/14 21:20
    * @Params: String name 系统名称
    * @Return  Os
    */
    @Override
    public Os selectOsByName(String name) {
        QueryWrapper<Os> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("name",name);
        return this.getOne(queryWrapper);
    }

}

