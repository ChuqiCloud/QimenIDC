package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.VmhostDao;
import com.chuqiyun.proxmoxveams.entity.VmParams;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.utils.TimeUtil;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * (Vmhost)表服务实现类
 *
 * @author mryunqi
 * @since 2023-06-21 15:11:18
 */
@Service("vmhostService")
public class VmhostServiceImpl extends ServiceImpl<VmhostDao, Vmhost> implements VmhostService {

    /**
    * @Author: mryunqi
    * @Description: 根据虚拟机id获取虚拟机实例信息
    * @DateTime: 2023/6/22 1:37
    */
    @Override
    public Vmhost getVmhostByVmId(int vmId) {
        return this.getOne(new QueryWrapper<Vmhost>().eq("vmid",vmId));
    }

    /**
    * @Author: mryunqi
    * @Description: 添加虚拟机实例信息
    * @DateTime: 2023/6/21 23:54
    */
    @Override
    public Integer addVmhost(int vmId,VmParams vmParams) {
        Vmhost vmhost = new Vmhost();
        vmhost.setNodeid(vmParams.getNodeid());
        vmhost.setVmid(vmId);
        vmhost.setName(vmParams.getHostname());
        vmhost.setCores(vmParams.getCores());
        vmhost.setMemory(vmParams.getMemory());
        vmhost.setStorage(vmParams.getStorage());
        vmhost.setSystemDiskSize(vmParams.getSystemDiskSize());
        vmhost.setDataDisk(vmParams.getDataDisk());
        vmhost.setBridge(vmParams.getBridge());
        vmhost.setOs(vmParams.getOs());
        vmhost.setBandwidth(vmParams.getBandwidth());
        vmhost.setTask(vmParams.getTask());
        vmhost.setCreateTime(System.currentTimeMillis());
        // 判断到期时间是否为空
        if (vmParams.getExpirationTime() != null) {
            // 时间设定为99年后到期
            vmhost.setExpirationTime(System.currentTimeMillis()+315360000000L);
        }
        // 判断到期时间是否为10位
        if (vmParams.getExpirationTime() != null && vmParams.getExpirationTime().toString().length() == 10) {
            // 将时间转换为13位
            vmhost.setExpirationTime(TimeUtil.tenToThirteen(vmParams.getExpirationTime()));
        }
        // 返回id
        return this.save(vmhost) ? vmhost.getId() : null;
    }
}

