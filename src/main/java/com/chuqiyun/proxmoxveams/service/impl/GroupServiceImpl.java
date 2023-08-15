package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.GroupDao;
import com.chuqiyun.proxmoxveams.entity.Group;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.service.GroupService;
import com.chuqiyun.proxmoxveams.service.MasterService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * (Group)表服务实现类
 *
 * @author mryunqi
 * @since 2023-08-14 18:14:15
 */
@Service("groupService")
public class GroupServiceImpl extends ServiceImpl<GroupDao, Group> implements GroupService {
    @Resource
    private MasterService masterService;

    /**
    * @Author: mryunqi
    * @Description: 分页查询绑定指定地区id的节点
    * @DateTime: 2023/8/15 23:26
    * @Params: Integer groupId 地区id, Integer page 页数, Integer limit 每页数量
    * @Return  Page<Master> 分页查询结果
    */
    @Override
    public Page<Master> selectGroupInNode(Integer groupId, Integer page, Integer limit) {
        Page<Master> groupPage = new Page<>(page,limit);
        QueryWrapper<Master> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("group",groupId);
        return masterService.page(groupPage,queryWrapper);
    }


    /**
    * @Author: mryunqi
    * @Description: 将所有绑定指定地区id的节点绑定为0
    * @DateTime: 2023/8/15 23:24
    * @Params: Integer id 地区id
    * @Return void
    */
    @Override
    public void updateGroupBindNode(Integer id) {
        int i = 1;
        while (true){
            Page<Master> groupPage = selectGroupInNode(id, i, 100);
            if (groupPage.getRecords().size() == 0){
                break;
            }
            // 将所有绑定指定地区id的节点绑定为0
            for (Master master : groupPage.getRecords()) {
                master.setGroup(0);
            }
            // 批量更新
            masterService.updateBatchById(groupPage.getRecords());
            // 判断是否还有下一页
            if (i >= groupPage.getPages()){
                break;
            }
            i++;
        }

    }

    /**
    * @Author: mryunqi
    * @Description: 判断是否存在子集地区
    * @DateTime: 2023/8/15 23:38
    * @Params: Integer id 地区id
    * @Return boolean
    */
    @Override
    public boolean isExistChild(Integer id) {
        QueryWrapper<Group> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("parent",id);
        return count(queryWrapper) > 0;
    }

    /**
    * @Author: mryunqi
    * @Description: 分页查询地区
    * @DateTime: 2023/8/15 23:42
    * @Params: Integer page 页数, Integer limit 每页数量
    * @Return Page<Group> 分页查询结果
    */
    @Override
    public Page<Group> selectGroupPage(Integer page, Integer limit) {
        Page<Group> groupPage = new Page<>(page,limit);
        return page(groupPage);
    }
    /**
    * @Author: mryunqi
    * @Description: 带条件分页查询地区
    * @DateTime: 2023/8/15 23:42
    * @Params: Integer page 页数, Integer limit 每页数量, QueryWrapper<Group> queryWrapper 条件
    * @Return Page<Group> 分页查询结果
    */
    @Override
    public Page<Group> selectGroupPage(Integer page, Integer limit, QueryWrapper<Group> queryWrapper) {
        Page<Group> groupPage = new Page<>(page,limit);
        return page(groupPage,queryWrapper);
    }

}

