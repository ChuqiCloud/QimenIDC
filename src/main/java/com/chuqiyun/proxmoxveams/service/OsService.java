package com.chuqiyun.proxmoxveams.service;

import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.Os;

/**
 * (Os)表服务接口
 *
 * @author mryunqi
 * @since 2023-07-08 15:58:22
 */
public interface OsService extends IService<Os> {

    Os selectOsByName(String name);

    Os selectOsByNameAndNodeId(String name, Integer nodeId);

    JSONArray selectOsByOsName(String osName);
}

