package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.Group;
import com.chuqiyun.proxmoxveams.entity.Master;

/**
 * (Group)表服务接口
 *
 * @author mryunqi
 * @since 2023-08-14 18:14:15
 */
public interface GroupService extends IService<Group> {

    Page<Master> selectGroupInNode(Integer groupId, Integer page, Integer limit);

    void updateGroupBindNode(Integer id);

    boolean isExistChild(Integer id);

    Page<Group> selectGroupPage(Integer page, Integer limit);

    Page<Group> selectGroupPage(Integer page, Integer limit, QueryWrapper<Group> queryWrapper);
}

