package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.VncnodeDao;
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

}

