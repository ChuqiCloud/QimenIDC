package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.IppoolDao;
import com.chuqiyun.proxmoxveams.entity.Ippool;
import com.chuqiyun.proxmoxveams.service.IppoolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * (Ippool)表服务实现类
 *
 * @author mryunqi
 * @since 2023-07-02 19:08:38
 */
@Slf4j
@Service("ippoolService")
public class IppoolServiceImpl extends ServiceImpl<IppoolDao, Ippool> implements IppoolService {
    /**
    * @Author: mryunqi
    * @Description: 批量插入ip池
    * @DateTime: 2023/7/2 22:05
    * @Params: List<Ippool> ippoolList
    * @Return boolean
    */
    @Override
    public boolean insertIppoolList(List<Ippool> ippoolList) {
        if (ippoolList == null || ippoolList.isEmpty()) {
            return true;
        }
        List<Ippool> insertList = new ArrayList<>();
        Set<String> seenKeySet = new LinkedHashSet<>();
        for (Ippool ippool : ippoolList) {
            if (ippool == null || ippool.getNodeId() == null || ippool.getIpVersion() == null || ippool.getIp() == null) {
                continue;
            }
            String ip = ippool.getIp().trim();
            String key = ippool.getNodeId() + "|" + ippool.getIpVersion() + "|" + ip;
            if (!seenKeySet.add(key)) {
                log.warn("[Ippool] 跳过本次批量插入中的重复IP: NodeId={}, IpVersion={}, Ip={}",
                        ippool.getNodeId(), ippool.getIpVersion(), ip);
                continue;
            }
            QueryWrapper<Ippool> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("node_id", ippool.getNodeId());
            queryWrapper.eq("ip_version", ippool.getIpVersion());
            queryWrapper.eq("ip", ip);
            queryWrapper.last("limit 1");
            if (this.getOne(queryWrapper) != null) {
                log.warn("[Ippool] 跳过数据库已存在IP: NodeId={}, IpVersion={}, Ip={}",
                        ippool.getNodeId(), ippool.getIpVersion(), ip);
                continue;
            }
            ippool.setIp(ip);
            insertList.add(ippool);
        }
        return insertList.isEmpty() || this.saveBatch(insertList,254);
    }

    /**
    * @Author: mryunqi
    * @Description: 判断网关是否在ip池中
    * @DateTime: 2023/7/2 23:00
    * @Params: String gateway
    * @Return boolean
    */
    @Override
    public boolean isGatewayInIppool(String gateway) {
        return this.lambdaQuery().eq(Ippool::getGateway,gateway).count() > 0;
    }

    /**
    * @Author: mryunqi
    * @Description: 获取指定网关IP实体类列表
    * @DateTime: 2023/7/3 22:50
    * @Params: String gateway
    * @Return  List<Ippool>
    */
    @Override
    public List<Ippool> getIppoolListByGateway(String gateway) {
        return this.lambdaQuery().eq(Ippool::getGateway,gateway).list();
    }
    /**
    * @Author: mryunqi
    * @Description: 获取指定网关IP列表
    * @DateTime: 2023/7/3 22:52
    * @Params: String gateway
    * @Return List<String
    */
    @Override
    public List<String> getIpListByGateway(String gateway) {
        return this.lambdaQuery().eq(Ippool::getGateway,gateway).list().stream().map(Ippool::getIp).collect(Collectors.toList());
    }
    /**
    * @Author: mryunqi
    * @Description: 获取指定IP池ID IP列表
    * @DateTime: 2023/7/3 22:55
    * @Params: Integer ippoolId
    * @Return List<String>
    */
    @Override
    public List<String> getIpListByPoolId(Integer ippoolId) {
        return this.lambdaQuery().eq(Ippool::getPoolId,ippoolId).list().stream().map(Ippool::getIp).collect(Collectors.toList());
    }
    /**
    * @Author: mryunqi
    * @Description: 获取指定IP池ID IP实体类列表
    * @DateTime: 2023/7/3 22:56
    * @Params: Integer ippoolId
    * @Return List<Ippool>
    */
    @Override
    public List<Ippool> getIppoolListByPoolId(Integer ippoolId) {
        return this.lambdaQuery().eq(Ippool::getPoolId,ippoolId).list();
    }
    /**
    * @Author: mryunqi
    * @Description: 获取指定IP池ID IP实体类分页列表
    * @DateTime: 2023/7/4 16:22
    * @Params: Integer ippoolId, Integer page, Integer limit
    * @Return Page<Ippool>
    */
    @Override
    public Page<Ippool> getIppoolListByPoolId(Integer ippoolId, Integer page, Integer limit) {
        return this.lambdaQuery().eq(Ippool::getPoolId,ippoolId).page(new Page<>(page,limit));
    }

    /**
     * @Author: 星禾
     * @Description: 获取指定节点空闲IP分页列表
     * @DateTime: 2026/6/4 20:14
     */
    @Override
    public Page<Ippool> getFreeIppoolListByNodeId(Integer nodeId, Integer page, Integer limit, Integer poolId) {
        return getFreeIppoolListByNodeId(nodeId, page, limit, poolId, null);
    }

    @Override
    public Page<Ippool> getFreeIppoolListByNodeId(Integer nodeId, Integer page, Integer limit, Integer poolId, Integer ipVersion) {
        QueryWrapper<Ippool> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("node_id", nodeId);
        queryWrapper.eq("status", 0);
        if (poolId != null) {
            queryWrapper.eq("pool_id", poolId);
        }
        if (ipVersion != null) {
            queryWrapper.eq("ip_version", ipVersion);
        }
        queryWrapper.orderByAsc("id");
        return this.page(new Page<>(page, limit), queryWrapper);
    }
    /**
    * @Author: mryunqi
    * @Description: 批量更新ip池
    * @DateTime: 2023/7/4 16:46
    * @Params: List<Ippool> ippoolList
    * @Return boolean
    */
    @Override
    public boolean updateIppoolList(List<Ippool> ippoolList) {
        return this.updateBatchById(ippoolList,254);
    }
    /**
    * @Author: mryunqi
    * @Description: 获取所有ID列表
    * @DateTime: 2023/7/4 17:23
    */
    @Override
    public List<Integer> getAllIdList() {
        return this.lambdaQuery().select(Ippool::getId).list().stream().map(Ippool::getId).collect(Collectors.toList());
    }
    /**
    * @Author: mryunqi
    * @Description: 获取指定条件IP数量
    * @DateTime: 2023/7/4 17:28
    * @Params: QueryWrapper<Ippool> ippool
    * @Return Integer
    */
    @Override
    public Integer getIpCountByCondition(QueryWrapper<Ippool> ippool) {
        return Math.toIntExact(this.count(ippool));
    }
    /**
    * @Author: mryunqi
    * @Description: 获取指定IP池ID IP数量
    * @DateTime: 2023/7/4 17:32
    * @Params:  Integer ippoolId
    * @Return Long
    */
    @Override
    public Long getIpCountByPoolId(Integer ippoolId) {
        return this.lambdaQuery().eq(Ippool::getPoolId,ippoolId).count();
    }
    /**
    * @Author: mryunqi
    * @Description: 获取指定ID可用IP其中一个
    * @DateTime: 2023/7/6 18:40
    * @Params: Integer ippoolId
    * @Return String
    */
    @Override
    public Ippool getOneOkIpByPoolId(Integer ippoolId) {
        return this.lambdaQuery().eq(Ippool::getPoolId,ippoolId).eq(Ippool::getStatus,0).last("limit 1").one();
    }

    /**
     * @Author: 星禾
     * @Description: 获取指定节点的一个空闲IP
     * @DateTime: 2026/6/4 20:14
     */
    @Override
    public Ippool getOneFreeIpByNodeId(Integer nodeId, Integer poolId) {
        return getOneFreeIpByNodeId(nodeId, poolId, null);
    }

    @Override
    public Ippool getOneFreeIpByNodeId(Integer nodeId, Integer poolId, Integer ipVersion) {
        QueryWrapper<Ippool> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("node_id", nodeId);
        queryWrapper.eq("status", 0);
        if (poolId != null) {
            queryWrapper.eq("pool_id", poolId);
        }
        if (ipVersion != null) {
            queryWrapper.eq("ip_version", ipVersion);
        }
        queryWrapper.orderByAsc("id");
        queryWrapper.last("limit 1");
        return this.getOne(queryWrapper);
    }

    /**
    * @Author: mryunqi
    * @Description: 根据ip地址查询ip实体类
    * @DateTime: 2023/10/30 19:40
    * @Params: String ip ip地址
    * @Return Ippool ip实体类
    */
    @Override
    public Ippool getIppoolByIp(String ip) {
        return this.lambdaQuery().eq(Ippool::getIp, ip).one();
    }

    @Override
    public boolean bindFreeIppool(Integer ippoolId, Integer vmId) {
        if (ippoolId == null || vmId == null) {
            return false;
        }
        UpdateWrapper<Ippool> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", ippoolId);
        updateWrapper.eq("status", 0);
        updateWrapper.set("status", 1);
        updateWrapper.set("vm_id", vmId);
        boolean success = this.update(updateWrapper);
        if (!success) {
            log.warn("[Ippool] 绑定IP失败，IP可能已被其他任务占用: IppoolId={}, VmId={}", ippoolId, vmId);
        }
        return success;
    }

    @Override
    public boolean releaseBoundIppool(Integer ippoolId, Integer vmId) {
        if (ippoolId == null || vmId == null) {
            return false;
        }
        UpdateWrapper<Ippool> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", ippoolId);
        updateWrapper.eq("status", 1);
        updateWrapper.eq("vm_id", vmId);
        updateWrapper.set("status", 0);
        updateWrapper.set("vm_id", 0);
        boolean success = this.update(updateWrapper);
        if (!success) {
            log.warn("[Ippool] 释放IP被拦截，IP未绑定到当前VM: IppoolId={}, VmId={}", ippoolId, vmId);
        }
        return success;
    }

    /**
     * @Author: 星禾
     * @Description: 释放指定节点下虚拟机绑定的全部IP
     * @DateTime: 2026/6/6 12:27
     */
    @Override
    public int releaseIppoolByNodeIdAndVmId(Integer nodeId, Integer vmId, List<String> ipList) {
        Set<String> releaseIpSet = new LinkedHashSet<>();
        if (ipList != null) {
            for (String ip : ipList) {
                if (ip != null && !ip.trim().isEmpty()) {
                    releaseIpSet.add(ip.trim());
                }
            }
        }
        if (nodeId == null || (vmId == null && releaseIpSet.isEmpty())) {
            return 0;
        }

        QueryWrapper<Ippool> beforeWrapper = new QueryWrapper<>();
        beforeWrapper.eq("node_id", nodeId);
        beforeWrapper.eq("status", 1);
        beforeWrapper.eq("vm_id", vmId);
        if (!releaseIpSet.isEmpty()) {
            beforeWrapper.in("ip", releaseIpSet);
        }
        int releaseCount = getIpCountByCondition(beforeWrapper);
        if (releaseCount <= 0) {
            log.warn("[Ippool] 未找到可释放IP: NodeId={}, VmId={}, IpList={}", nodeId, vmId, releaseIpSet);
            return 0;
        }

        UpdateWrapper<Ippool> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("node_id", nodeId);
        updateWrapper.eq("status", 1);
        updateWrapper.eq("vm_id", vmId);
        if (!releaseIpSet.isEmpty()) {
            updateWrapper.in("ip", releaseIpSet);
        }
        updateWrapper.set("status", 0);
        updateWrapper.set("vm_id", 0);
        boolean success = this.update(updateWrapper);
        if (!success) {
            log.warn("[Ippool] 未释放任何IP: NodeId={}, VmId={}, IpList={}", nodeId, vmId, releaseIpSet);
            return 0;
        }
        return releaseCount;
    }

    @Override
    public void logIppoolConsistencyWarnings() {
        QueryWrapper<Ippool> duplicateWrapper = new QueryWrapper<>();
        duplicateWrapper.select("node_id", "ip_version", "ip", "count(*) duplicate_count");
        duplicateWrapper.groupBy("node_id", "ip_version", "ip");
        duplicateWrapper.having("count(*) > 1");
        List<Map<String, Object>> duplicateList = this.listMaps(duplicateWrapper);
        if (duplicateList != null && !duplicateList.isEmpty()) {
            for (Map<String, Object> item : duplicateList) {
                log.warn("[IppoolAudit] 发现IP池重复记录: NodeId={}, IpVersion={}, Ip={}, Count={}",
                        item.get("node_id"), item.get("ip_version"), item.get("ip"), item.get("duplicate_count"));
            }
        }

        QueryWrapper<Ippool> invalidBoundWrapper = new QueryWrapper<>();
        invalidBoundWrapper.eq("status", 1);
        invalidBoundWrapper.and(wrapper -> wrapper.isNull("vm_id").or().eq("vm_id", 0));
        int invalidBoundCount = getIpCountByCondition(invalidBoundWrapper);
        if (invalidBoundCount > 0) {
            log.warn("[IppoolAudit] 发现已占用但缺少VM绑定的IP数量: Count={}", invalidBoundCount);
        }

        QueryWrapper<Ippool> invalidFreeWrapper = new QueryWrapper<>();
        invalidFreeWrapper.eq("status", 0);
        invalidFreeWrapper.gt("vm_id", 0);
        int invalidFreeCount = getIpCountByCondition(invalidFreeWrapper);
        if (invalidFreeCount > 0) {
            log.warn("[IppoolAudit] 发现空闲但仍保留VM绑定的IP数量: Count={}", invalidFreeCount);
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 批量删除指定条件的所有IP
    * @DateTime: 2023/10/31 22:37
    * @Params: QueryWrapper<Ippool> ippool 条件
    * @Return  boolean 是否成功
    */
    @Override
    public boolean deleteIppoolByCondition(QueryWrapper<Ippool> ippool) {
        return this.remove(ippool);
    }
}

