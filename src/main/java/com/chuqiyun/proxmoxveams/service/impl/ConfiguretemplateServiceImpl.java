package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.ConfiguretemplateDao;
import com.chuqiyun.proxmoxveams.entity.Configuretemplate;
import com.chuqiyun.proxmoxveams.service.ConfiguretemplateService;
import org.springframework.stereotype.Service;

/**
 * 配置模板(Configuretemplate)表服务实现类
 *
 * @author mryunqi
 * @since 2023-09-21 22:10:13
 */
@Service("configuretemplateService")
public class ConfiguretemplateServiceImpl extends ServiceImpl<ConfiguretemplateDao, Configuretemplate> implements ConfiguretemplateService {

    /**
    * @Author: mryunqi
    * @Description: 新增配置模板
    * @DateTime: 2023/9/21 22:52
    * @Params: Configuretemplate configuretemplate 配置模板
    * @Return Boolean
    */
    @Override
    public Boolean addConfiguretemplate(Configuretemplate configuretemplate) {
        return this.save(configuretemplate);
    }

    /**
    * @Author: mryunqi
    * @Description: 分页查询配置模板
    * @DateTime: 2023/9/21 22:53
    * @Params: Integer page 当前页码, Integer limit 每页条数
    * @Return Page<Configuretemplate> 分页对象
    */
    @Override
    public Page<Configuretemplate> selectConfiguretemplatePage(Integer page, Integer limit) {
        Page<Configuretemplate> configuretemplatePage = new Page<>(page,limit);
        return this.page(configuretemplatePage);
    }

    /**
    * @Author: mryunqi
    * @Description: 根据id查询配置模板
    * @DateTime: 2023/9/22 17:03
    * @Params: Integer id 配置模板id
    * @Return Configuretemplate
    */
    @Override
    public Configuretemplate selectConfiguretemplateById(Integer id) {
        return this.getById(id);
    }

    /**
    * @Author: mryunqi
    * @Description: 根据id删除配置模板
    * @DateTime: 2023/9/21 23:08
    * @Params: Integer id 配置模板id
    * @Return  Boolean
    */
    @Override
    public Boolean deleteConfiguretemplateById(Integer id) {
        return this.removeById(id);
    }

    /**
    * @Author: mryunqi
    * @Description: 修改配置模板
    * @DateTime: 2023/9/21 23:10
    * @Params: Configuretemplate configuretemplate 配置模板
    * @Return Boolean
    */
    @Override
    public Boolean updateConfiguretemplate(Configuretemplate configuretemplate) {
        return this.updateById(configuretemplate);
    }
}

