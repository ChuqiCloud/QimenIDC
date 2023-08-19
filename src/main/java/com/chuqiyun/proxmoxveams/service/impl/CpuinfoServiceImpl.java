package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.CpuinfoDao;
import com.chuqiyun.proxmoxveams.entity.Cpuinfo;
import com.chuqiyun.proxmoxveams.service.CpuinfoService;
import org.springframework.stereotype.Service;

/**
 * (Cpuinfo)表服务实现类
 *
 * @author mryunqi
 * @since 2023-08-19 12:59:59
 */
@Service("cpuinfoService")
public class CpuinfoServiceImpl extends ServiceImpl<CpuinfoDao, Cpuinfo> implements CpuinfoService {
    /**
    * @Author: mryunqi
    * @Description: 新增cpu信息模型
    * @DateTime: 2023/8/19 23:13
    * @Params: Cpuinfo cpuinfo cpu信息模型
    * @Return Boolean
    */
    @Override
    public Boolean addCpuInfo(Cpuinfo cpuinfo) {
        return this.save(cpuinfo);
    }

    /**
    * @Author: mryunqi
    * @Description: 分页查询cpu信息模型
    * @DateTime: 2023/8/19 23:21
    * @Params: Integer page 当前页码, Integer limit 每页条数
    * @Return Page<Cpuinfo> 分页对象
    */
    @Override
    public Page<Cpuinfo> selectCpuInfoPage(Integer page, Integer limit) {
        Page<Cpuinfo> cpuinfoPage = new Page<>(page,limit);
        return this.page(cpuinfoPage);
    }

}

