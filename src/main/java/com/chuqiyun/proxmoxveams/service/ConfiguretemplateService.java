package com.chuqiyun.proxmoxveams.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.Configuretemplate;

/**
 * 配置模板(Configuretemplate)表服务接口
 *
 * @author mryunqi
 * @since 2023-09-21 22:10:13
 */
public interface ConfiguretemplateService extends IService<Configuretemplate> {

    Boolean addConfiguretemplate(Configuretemplate configuretemplate);

    Page<Configuretemplate> selectConfiguretemplatePage(Integer page, Integer limit);

    Configuretemplate selectConfiguretemplateById(Integer id);

    Boolean deleteConfiguretemplateById(Integer id);

    Boolean updateConfiguretemplate(Configuretemplate configuretemplate);
}

