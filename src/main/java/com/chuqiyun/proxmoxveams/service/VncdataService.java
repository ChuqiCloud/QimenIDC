package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.Vncdata;

/**
 * (Vncdata)表服务接口
 *
 * @author mryunqi
 * @since 2023-11-22 13:43:08
 */
public interface VncdataService extends IService<Vncdata> {

    boolean addVncdata(Vncdata vncdata);

    boolean deleteVncdata(Long id);

    boolean updateVncdata(Vncdata vncdata);

    Page<Vncdata> selectVncdataPage(Integer page, Integer limit);

    Page<Vncdata> selectVncdataPage(Integer page, Integer limit, QueryWrapper<Vncdata> queryWrapper);

    Vncdata selectVncdataByVncinfoId(Long vncinfoId);

    Vncdata selectVncdataByVncinfoIdAndStatusOk(Long vncinfoId);
}

