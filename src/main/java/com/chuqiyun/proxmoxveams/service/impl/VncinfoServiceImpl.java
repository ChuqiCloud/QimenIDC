package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.VncinfoDao;
import com.chuqiyun.proxmoxveams.entity.Vncinfo;
import com.chuqiyun.proxmoxveams.service.VncinfoService;
import org.springframework.stereotype.Service;

/**
 * (Vncinfo)表服务实现类
 *
 * @author mryunqi
 * @since 2023-11-22 13:42:50
 */
@Service("vncinfoService")
public class VncinfoServiceImpl extends ServiceImpl<VncinfoDao, Vncinfo> implements VncinfoService {

}

