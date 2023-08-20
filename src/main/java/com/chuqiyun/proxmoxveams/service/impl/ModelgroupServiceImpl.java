package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.ModelgroupDao;
import com.chuqiyun.proxmoxveams.entity.Modelgroup;
import com.chuqiyun.proxmoxveams.service.ModelgroupService;
import org.springframework.stereotype.Service;

/**
 * (Modelgroup)表服务实现类
 *
 * @author mryunqi
 * @since 2023-08-20 16:04:32
 */
@Service("modelgroupService")
public class ModelgroupServiceImpl extends ServiceImpl<ModelgroupDao, Modelgroup> implements ModelgroupService {
    /**
    * @Author: mryunqi
    * @Description: 新增modelgroup信息模型
    * @DateTime: 2023/8/20 17:49
    * @Params: Modelgroup modelgroup modelgroup信息模型
    * @Return  Boolean
    */
    @Override
    public Boolean addModelgroupInfo(Modelgroup modelgroup) {
        return this.save(modelgroup);
    }
}

