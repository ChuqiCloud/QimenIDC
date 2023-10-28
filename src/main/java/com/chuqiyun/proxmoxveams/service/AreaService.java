package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.Area;
import com.chuqiyun.proxmoxveams.entity.Master;

/**
 * (Area)表服务接口
 *
 * @author mryunqi
 * @since 2023-08-14 18:14:15
 */
public interface AreaService extends IService<Area> {

    Page<Master> selectGroupInNode(Integer areaId, Integer page, Integer limit);

    /**
    * @Author: mryunqi
    * @Description: 分页获取父级地区
    * @DateTime: 2023/10/28 13:34
    * @Params: Integer page 页数
     * @Params: Integer limit 每页数据
    * @Return  Page<Area>
    */
    Page<Area> selectParentPage(Integer page, Integer limit);

    void updateGroupBindNode(Integer id);

    boolean isExistChild(Integer id);

    Page<Area> selectGroupPage(Integer page, Integer limit);

    Page<Area> selectGroupPage(Integer page, Integer limit, QueryWrapper<Area> queryWrapper);

    Page<Area> selectGroupPageByParent(Integer page, Integer limit, Integer parent);

    Page<Area> selectGroupPageByParent(Integer page, Integer limit, Integer parent, QueryWrapper<Area> queryWrapper);

    boolean isExistName(String name);

    boolean isExistId(Integer id);
}

