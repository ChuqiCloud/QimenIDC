package com.chuqiyun.proxmoxveams.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.dto.RenewalParams;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.dto.VmParams;
import com.chuqiyun.proxmoxveams.entity.Os;
import com.chuqiyun.proxmoxveams.entity.Vmhost;

import java.util.Arrays;
import java.util.HashMap;

/**
 * (Vmhost)表服务接口
 *
 * @author mryunqi
 * @since 2023-06-21 15:11:18
 */
public interface VmhostService extends IService<Vmhost> {

    Vmhost getVmhostByVmId(int vmId);

    Page<Vmhost> selectPage(Integer page, Integer limit);

    Page<Vmhost> selectPage(Integer page, Integer limit, QueryWrapper<Vmhost> queryWrapper);

    Page<Vmhost> selectPageByCreateTime(Integer page, Integer limit);

    Page<Vmhost> selectPageByCreateTime(Integer page, Integer limit, QueryWrapper<Vmhost> queryWrapper);

    Page<Vmhost> selectPageByIp(Integer page, Integer limit, String ip);

    Vmhost getVmhostByName(String name);

    Page<Vmhost> selectPageByNodeId(Integer page, Integer limit, String nodeId);

    Page<Vmhost> selectPageByStatus(Integer page, Integer size, Integer status);

    Integer addVmhost(int vmId, VmParams vmParams);

    HashMap<String,Object> power(Integer hostId, String action, JSONObject data);

    Integer getNewVmid(Integer id);

    void syncVmStatus(JSONArray vmHosts, Integer nodeId);

    UnifiedResultDto<Object> resetVmOs(Long vmHostId, String osName, String newPassword, Boolean resetDataDisk);

    UnifiedResultDto<Object> deleteVm(Long vmHostId);

    UnifiedResultDto<Object> resetVmPassword(Long vmHostId, String newPassword);

    Boolean addVmHostTask(Object hostId, Object taskId);

    UnifiedResultDto<Object> updateVmhostExpireTime(RenewalParams renewalParams);

    void updateVmhostOsData(long vmId, Os os);

    Page<Vmhost> getVmhostByStatus(Long page, Long size, int status);

    Long selectCount(QueryWrapper<Vmhost> queryWrapper);

    Long getVmhostCountByStatus(int status);

    Integer getVmhostNodeId(int hostId);

    Object getVmhostNatByVmid(int page, int size, int hostId);
    Boolean addVmhostNat(int source_port, String destination_ip, int destination_port, String protocol , int vm);
    Boolean delVmhostNat(int source_port, String destination_ip, int destination_port, String protocol , int vm);
}

