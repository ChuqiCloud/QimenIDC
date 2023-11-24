package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.VncnodeDao;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.entity.Vncnode;
import com.chuqiyun.proxmoxveams.service.VncnodeService;
import org.springframework.stereotype.Service;

/**
 * (Vncnode)表服务实现类
 *
 * @author mryunqi
 * @since 2023-11-22 13:42:25
 */
@Service("vncnodeService")
public class VncnodeServiceImpl extends ServiceImpl<VncnodeDao, Vncnode> implements VncnodeService {

    /**
    * @Author: mryunqi
    * @Description: 添加VNC控制器节点
    * @DateTime: 2023/11/22 21:13
    * @Params: Vncnode vncnode VNC控制器节点实体类
    * @Return boolean
    */
    @Override
    public boolean addVncnode(Vncnode vncnode) {
        return this.save(vncnode);
    }

    /**
    * @Author: mryunqi
    * @Description: 删除VNC控制器节点
    * @DateTime: 2023/11/22 21:14
    * @Params: Long id VNC控制器节点ID
    * @Return  boolean
    */
    @Override
    public boolean deleteVncnode(Long id) {
        return this.removeById(id);
    }

    /**
    * @Author: mryunqi
    * @Description: 修改VNC控制器节点
    * @DateTime: 2023/11/22 21:15
    * @Params: Vncnode vncnode VNC控制器节点实体类
    * @Return boolean
    */
    @Override
    public boolean updateVncnode(Vncnode vncnode) {
        return this.updateById(vncnode);
    }

    /**
    * @Author: mryunqi
    * @Description: 分页查询VNC控制器节点
    * @DateTime: 2023/11/22 21:15
    * @Params: Integer page 当前页 Integer limit 每页条数
    * @Return Page<Vncnode> VNC控制器节点分页数据
    */
    @Override
    public Page<Vncnode> selectVncnodePage(Integer page, Integer limit) {
        Page<Vncnode> page1 = new Page<>(page, limit);
        return this.page(page1);
    }

    /**
    * @Author: mryunqi
    * @Description: 带参数分页查询VNC控制器节点
    * @DateTime: 2023/11/22 21:17
    * @Params: Integer page 当前页 Integer limit 每页条数 QueryWrapper<Vncnode> queryWrapper VNC控制器节点查询条件
    * @Return Page<Vncnode> VNC控制器节点分页数据
    */
    @Override
    public Page<Vncnode> selectVncnodePage(Integer page, Integer limit, QueryWrapper<Vncnode> queryWrapper) {
        Page<Vncnode> page1 = new Page<>(page, limit);
        return this.page(page1, queryWrapper);
    }

    /**
    * @Author: mryunqi
    * @Description: 添加宿主机本体控制器
    * @DateTime: 2023/11/23 17:00
    * @Params: Master 宿主机本体控制器实体类
    * @Return  boolean
    */
    @Override
    public boolean addHostVncnode(Master master) {
        Vncnode vncnode = new Vncnode();
        vncnode.setName(master.getName());
        vncnode.setHost(master.getHost());
        vncnode.setPort(master.getControllerPort());
        vncnode.setDomain(master.getHost());
        vncnode.setStatus(0);
        vncnode.setCreateDate(System.currentTimeMillis());
        return this.save(vncnode);
    }

    /**
    * @Author: mryunqi
    * @Description: 根据host查询VNC控制器节点
    * @DateTime: 2023/11/24 17:23
    * @Params: String host 宿主机IP
    * @Return Vncnode VNC控制器节点实体类
    */
    @Override
    public Vncnode selectVncnodeByHost(String host) {
        QueryWrapper<Vncnode> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("host", host);
        return this.getOne(queryWrapper);
    }

}

