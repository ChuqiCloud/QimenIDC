package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.VncdataDao;
import com.chuqiyun.proxmoxveams.entity.Vncdata;
import com.chuqiyun.proxmoxveams.service.VncdataService;
import org.springframework.stereotype.Service;

/**
 * (Vncdata)表服务实现类
 *
 * @author mryunqi
 * @since 2023-11-22 13:43:08
 */
@Service("vncdataService")
public class VncdataServiceImpl extends ServiceImpl<VncdataDao, Vncdata> implements VncdataService {

}

