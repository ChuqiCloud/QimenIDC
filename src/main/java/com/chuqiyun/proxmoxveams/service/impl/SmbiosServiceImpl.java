package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.SmbiosDao;
import com.chuqiyun.proxmoxveams.entity.Smbios;
import com.chuqiyun.proxmoxveams.service.SmbiosService;
import org.springframework.stereotype.Service;

/**
 * (Smbios)表服务实现类
 *
 * @author mryunqi
 * @since 2023-08-19 13:00:19
 */
@Service("smbiosService")
public class SmbiosServiceImpl extends ServiceImpl<SmbiosDao, Smbios> implements SmbiosService {

}

