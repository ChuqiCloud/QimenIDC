package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.Modelgroup;

/**
 * (Modelgroup)表服务接口
 *
 * @author mryunqi
 * @since 2023-08-20 16:04:32
 */
public interface ModelgroupService extends IService<Modelgroup> {

    Boolean addModelgroupInfo(Modelgroup modelgroup);
}

