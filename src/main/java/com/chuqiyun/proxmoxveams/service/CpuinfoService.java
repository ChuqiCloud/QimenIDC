package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.Cpuinfo;

/**
 * (Cpuinfo)表服务接口
 *
 * @author mryunqi
 * @since 2023-08-19 12:59:59
 */
public interface CpuinfoService extends IService<Cpuinfo> {

    Boolean addCpuInfo(Cpuinfo cpuinfo);

    Page<Cpuinfo> selectCpuInfoPage(Integer page, Integer limit);
}

