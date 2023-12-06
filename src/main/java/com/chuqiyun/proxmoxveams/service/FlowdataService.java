package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.Flowdata;

/**
 * (Flowdata)表服务接口
 *
 * @author mryunqi
 * @since 2023-12-03 20:21:54
 */
public interface FlowdataService extends IService<Flowdata> {

    Boolean insertFlowdata(Flowdata flowdata);

    Boolean deleteFlowdataById(Integer id);

    Boolean updateFlowdata(Flowdata flowdata);

    Page<Flowdata> selectFlowdataByPage(Integer page, Integer size);

    Page<Flowdata> selectFlowdataByPageAndWrapper(Integer page, Integer size, QueryWrapper<Flowdata> queryWrapper);

    Flowdata selectFlowdataByHostid(Integer hostid);
}

