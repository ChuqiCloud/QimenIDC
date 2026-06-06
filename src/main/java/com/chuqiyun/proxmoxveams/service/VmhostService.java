package com.chuqiyun.proxmoxveams.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chuqiyun.proxmoxveams.dto.RenewalParams;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.dto.VmIpParams;
import com.chuqiyun.proxmoxveams.dto.VmParams;
import com.chuqiyun.proxmoxveams.entity.Os;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import org.springframework.web.bind.annotation.RequestBody;

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

    Page<Vmhost> getVmhostByName(Integer page, Integer limit, String hostname);

    Vmhost getVmhostByNameOne(String hostname);

    Page<Vmhost> selectPageByNodeId(Integer page, Integer limit, String nodeId);

    Page<Vmhost> selectPageByStatus(Integer page, Integer size, Integer status);

    Integer addVmhost(int vmId, VmParams vmParams);

    HashMap<String,Object> power(Integer hostId, String action, JSONObject data);

    Integer getNewVmid(Integer id);

    void syncVmStatus(JSONArray vmHosts, Integer nodeId);

    UnifiedResultDto<Object> resetVmOs(Long vmHostId, String osName, String newPassword, Boolean resetDataDisk);

    UnifiedResultDto<Object> deleteVm(Long vmHostId);
    UnifiedResultDto<Object> deleteVmToRecycle(Long vmHostId);
    UnifiedResultDto<Object> unDeleteVm(Long hostId);

    UnifiedResultDto<Object> updateVm(VmParams vmParams);

    UnifiedResultDto<Object> updateVmIp(VmIpParams vmIpParams);

    UnifiedResultDto<Object> addVmIp(VmIpParams vmIpParams);

    UnifiedResultDto<Object> deleteVmIp(VmIpParams vmIpParams);

    UnifiedResultDto<Object> resetVmPassword(Long vmHostId, String newPassword);

    Boolean addVmHostTask(Object hostId, Object taskId);

    UnifiedResultDto<Object> updateVmhostExpireTime(RenewalParams renewalParams);

    void updateVmhostOsData(long vmId, Os os);

    Page<Vmhost> getVmhostByStatus(Long page, Long size, int status);

    Long selectCount(QueryWrapper<Vmhost> queryWrapper);

    Long getVmhostCountByStatus(int status);

    Integer getVmhostNodeId(int hostId);
    Boolean resetVmHostStatus(int hostId);
    Boolean resetVmHostFlow(int hostId);
    Boolean addVmHostFlow(int hostId, Long flow);
    Object getVmhostNatByVmid(int page, int size, int hostId);
    Object getVmhostNatAddrByVmid(int hostId);
    Boolean addVmhostNat(String source_ip, int source_port, String destination_ip, int destination_port, String protocol , int vm);
    Boolean delVmhostNat(String source_ip, int source_port, String destination_ip, int destination_port, String protocol , int vm);
    Boolean changeVmHostBandWidth(Vmhost vmhost, String bandwidth);

    Page<Vmhost> selectPageByDelete(Integer page, Integer limit, QueryWrapper<Vmhost> queryWrapper);

    Page<Vmhost> selectPageByDelete(Integer page, Integer size);

    JSONObject getVmSnapShot(Vmhost vmhost);
    boolean addVmSnapShot(Vmhost vmhost, String snapName , Boolean vmstate, String description);
    boolean deleteVmSnapShot(Vmhost vmhost, String snapName);
    boolean rollbackVmSnapShot(Vmhost vmhost, String snapName);
    /**
     * @Author: 鏄熺
     * @Description: 获取指定虚拟机备份列表
     * @DateTime: 2026/5/29 23:03
     */
    JSONObject getVmBackup(Vmhost vmhost);
    /**
     * @Author: 鏄熺
     * @Description: 创建指定虚拟机备份
     * @DateTime: 2026/5/29 23:03
     */
    JSONObject addVmBackup(Vmhost vmhost, String mode, String compress, String notes);
    /**
     * @Author: 鏄熺
     * @Description: 删除指定虚拟机备份
     * @DateTime: 2026/5/29 23:03
     */
    JSONObject deleteVmBackup(Vmhost vmhost, String volid);
    /**
     * @Author: 鏄熺
     * @Description: 还原指定虚拟机备份
     * @DateTime: 2026/5/29 23:03
     */
    JSONObject rollbackVmBackup(Vmhost vmhost, String volid, Boolean force, Boolean start);
}
