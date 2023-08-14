package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.GroupDao;
import com.chuqiyun.proxmoxveams.entity.Group;
import com.chuqiyun.proxmoxveams.service.GroupService;
import org.springframework.stereotype.Service;

/**
 * (Group)表服务实现类
 *
 * @author mryunqi
 * @since 2023-08-14 18:14:15
 */
@Service("groupService")
public class GroupServiceImpl extends ServiceImpl<GroupDao, Group> implements GroupService {

}

