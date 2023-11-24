package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.Vncinfo;

/**
 * (Vncinfo)表服务接口
 *
 * @author mryunqi
 * @since 2023-11-22 13:42:50
 */
public interface VncinfoService extends IService<Vncinfo> {
    /**
    * @Author: mryunqi
    * @Description: 添加虚拟机VNC配置信息
    * @DateTime: 2023/11/24 15:54
    * @Params: Vncinfo vncinfo 虚拟机VNC配置信息实体类
    * @Return  boolean
    */
    boolean addVncinfo(Vncinfo vncinfo);

    boolean deleteVncinfo(Long id);

    boolean updateVncinfo(Vncinfo vncinfo);

    Page<Vncinfo> selectVncinfoPage(Integer page, Integer limit);

    Page<Vncinfo> selectVncinfoPage(Integer page, Integer limit, QueryWrapper<Vncinfo> queryWrapper);

    Vncinfo selectVncinfoByHostId(Long hostId);
}

