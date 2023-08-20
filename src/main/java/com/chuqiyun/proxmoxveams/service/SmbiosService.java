package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.Smbios;

/**
 * (Smbios)表服务接口
 *
 * @author mryunqi
 * @since 2023-08-19 13:00:19
 */
public interface SmbiosService extends IService<Smbios> {

    Boolean addSmbiosInfo(Smbios smbios);

    Page<Smbios> selectSmbiosInfoPage(Integer page, Integer limit);
}

