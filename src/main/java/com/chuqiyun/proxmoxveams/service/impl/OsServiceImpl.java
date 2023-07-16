package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.OsDao;
import com.chuqiyun.proxmoxveams.entity.Os;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.OsService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * (Os)表服务实现类
 *
 * @author mryunqi
 * @since 2023-07-08 15:58:22
 */
@Service("osService")
public class OsServiceImpl extends ServiceImpl<OsDao, Os> implements OsService {
    @Resource
    private MasterService masterService;
    /**
    * @Author: mryunqi
    * @Description: 查询指定名称os是否存在
    * @DateTime: 2023/7/14 21:20
    * @Params: String name 系统名称
    * @Return  Os
    */
    @Override
    public Os selectOsByName(String name) {
        QueryWrapper<Os> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("name",name);
        return this.getOne(queryWrapper);
    }
    /**
    * @Author: mryunqi
    * @Description: 获取指定名称指定nodeId的os
    * @DateTime: 2023/7/16 11:40
    * @Params: String name 系统名称 String nodeId 节点id
    * @Return Os
    */
    @Override
    public Os selectOsByNameAndNodeId(String name, Integer nodeId) {
        QueryWrapper<Os> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("name",name);
        queryWrapper.eq("node_id",nodeId);
        return this.getOne(queryWrapper);
    }
    /**
    * @Author: mryunqi
    * @Description: 获取指定名称的os的所有节点信息
    * @DateTime: 2023/7/16 12:20
    * @Params: String osName 系统名称
    * @Return JSONArray
    */
    @Override
    public JSONArray selectOsByOsName(String osName){
        // 获取所有节点id
        List<Integer> nodeIdList = masterService.getAllNodeIdList();
        JSONArray jsonArray = new JSONArray();
        for (Integer nodeId : nodeIdList){
            Os os = selectOsByNameAndNodeId(osName, nodeId);
            if (os!=null){
                // 已安装
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("nodeId",nodeId);
                jsonObject.put("status",os.getStatus());
                jsonObject.put("osId",os.getId());
                jsonObject.put("size",os.getSize());
                jsonObject.put("path",os.getPath());
                // 判断是否正在下载
                if (os.getStatus()==1){
                    jsonObject.put("schedule",os.getSchedule());
                }
                jsonObject.put("createTime",os.getCreateTime());
                jsonArray.add(jsonObject);
            }
        }
        return jsonArray;
    }

}

