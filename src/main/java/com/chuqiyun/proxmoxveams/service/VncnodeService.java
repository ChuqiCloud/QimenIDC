package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.Vncnode;

/**
 * (Vncnode)表服务接口
 *
 * @author mryunqi
 * @since 2023-11-22 13:42:25
 */
public interface VncnodeService extends IService<Vncnode> {

    boolean addVncnode(Vncnode vncnode);

    boolean deleteVncnode(Long id);

    boolean updateVncnode(Vncnode vncnode);

    Page<Vncnode> selectVncnodePage(Integer page, Integer limit);

    Page<Vncnode> selectVncnodePage(Integer page, Integer limit, QueryWrapper<Vncnode> queryWrapper);
}

