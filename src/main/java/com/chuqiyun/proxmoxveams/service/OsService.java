package com.chuqiyun.proxmoxveams.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.entity.Os;
import com.chuqiyun.proxmoxveams.dto.OsParams;

import java.util.HashMap;

/**
 * (Os)表服务接口
 *
 * @author mryunqi
 * @since 2023-07-08 15:58:22
 */
public interface OsService extends IService<Os> {

    Os selectOsByName(String name);

    Os selectOsByFileName(String fileName);

    boolean isExistOsByName(String name);

    boolean isExistOsByFileName(String fileName);

    JSONArray selectOsByOsName(String osName);

    HashMap<String, Object> insertOs(OsParams osParams);

    Page<Os> selectOsByPage(int page, int limit);

    Page<Os> selectOsByPage(int page, int limit, QueryWrapper<Os> osQueryWrapper);

    boolean downloadOs(Integer osId, Integer nodeId);

    JSONObject getDownloadProgress(Integer osId, Integer nodeId);

    boolean deleteNodeOs(String osName, Integer nodeId);

    boolean deleteOs(Integer osId);

    Os isExistOs(String osName);

    Integer getNodeOsStatus(String osName, Integer nodeId);
}

