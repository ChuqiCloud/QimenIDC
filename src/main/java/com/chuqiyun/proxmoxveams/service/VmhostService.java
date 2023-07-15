package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.VmParams;
import com.chuqiyun.proxmoxveams.entity.Vmhost;

/**
 * (Vmhost)表服务接口
 *
 * @author mryunqi
 * @since 2023-06-21 15:11:18
 */
public interface VmhostService extends IService<Vmhost> {

    Vmhost getVmhostByVmId(int vmId);

    Integer addVmhost(int vmId, VmParams vmParams);
}

