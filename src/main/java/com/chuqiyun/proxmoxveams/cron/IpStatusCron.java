package com.chuqiyun.proxmoxveams.cron;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.entity.Ippool;
import com.chuqiyun.proxmoxveams.entity.Ipstatus;
import com.chuqiyun.proxmoxveams.entity.Subnetpool;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.entity.VpcIpBinding;
import com.chuqiyun.proxmoxveams.service.IppoolService;
import com.chuqiyun.proxmoxveams.service.IpstatusService;
import com.chuqiyun.proxmoxveams.service.SubnetpoolService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import com.chuqiyun.proxmoxveams.service.VpcIpBindingService;
import com.chuqiyun.proxmoxveams.utils.CloudInitNetworkUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author mryunqi
 * @date 2023/7/4
 */
@Slf4j
@Component
@EnableScheduling
public class IpStatusCron {
    private static final long IPPOOL_AUDIT_INTERVAL = 15 * 60 * 1000L;
    private static final int VMHOST_AUDIT_PAGE_SIZE = 500;
    private static final int VMHOST_AUDIT_DETAIL_LIMIT = 100;
    private static final String NETWORK_TYPE_VPC = "vpc";
    private volatile long lastIppoolAuditTime = 0L;

    @Resource
    private IpstatusService ipstatusService;
    @Resource
    private IppoolService ippoolService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private SubnetpoolService subnetpoolService;
    @Resource
    private VpcIpBindingService vpcIpBindingService;

    @Async
    @Scheduled(fixedDelay = 2000)
    public void ipStatusCron(){
        // 获取IP池所有ID
        List<Integer> poolIdList = ipstatusService.getAllId();
        // 遍历ID
        for (Integer poolId : poolIdList) {
            // 获取该ID下所有vmId为null且status为0的IP数量
            QueryWrapper<Ippool> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("pool_id",poolId);
            queryWrapper.eq("status",0);
            int available = ippoolService.getIpCountByCondition(queryWrapper);

            // 获取该ID下所有IP数量
            long allCount = ippoolService.getIpCountByPoolId(poolId);
            // 计算无效IP数量
            long disable = allCount - available;
            // 获取该ID下所有vmId不为null且status为1的IP数量
            queryWrapper.clear();
            queryWrapper.eq("pool_id",poolId);
            queryWrapper.eq("status",1);
            int used = ippoolService.getIpCountByCondition(queryWrapper);
            Ipstatus ipstatus = new Ipstatus();
            ipstatus.setId(poolId);
            ipstatus.setAvailable(available);
            ipstatus.setDisable((int) disable);
            ipstatus.setUsed(used);
            // 更新数据
            ipstatusService.updateById(ipstatus);
        }
        logIppoolConsistencyWarningsIfDue();
    }

    private void logIppoolConsistencyWarningsIfDue() {
        long now = System.currentTimeMillis();
        if (now - lastIppoolAuditTime < IPPOOL_AUDIT_INTERVAL) {
            return;
        }
        lastIppoolAuditTime = now;
        try {
            ippoolService.logIppoolConsistencyWarnings();
            auditVmhostIpBindings();
        } catch (Exception e) {
            log.warn("[IppoolAudit] IP池一致性巡检失败: {}", e.getMessage(), e);
        }
    }

    private void auditVmhostIpBindings() {
        VmhostAuditCounter counter = new VmhostAuditCounter();
        int[] detailCount = new int[]{0};
        long pageNo = 1L;
        Page<Vmhost> pageResult;
        do {
            QueryWrapper<Vmhost> queryWrapper = new QueryWrapper<>();
            queryWrapper.isNotNull("nodeid");
            queryWrapper.isNotNull("vmid");
            queryWrapper.and(wrapper -> wrapper.isNull("delete_state").or().eq("delete_state", 0));
            queryWrapper.orderByAsc("id");
            pageResult = vmhostService.page(new Page<>(pageNo, VMHOST_AUDIT_PAGE_SIZE), queryWrapper);
            for (Vmhost vmhost : pageResult.getRecords()) {
                auditOneVmhostIpBindings(vmhost, counter, detailCount);
            }
            pageNo++;
        } while (pageResult.getCurrent() < pageResult.getPages());

        if (counter.hasError()) {
            log.warn("[IppoolAudit] VM绑定巡检发现异常: VmCount={}, IpCount={}, ClassicMissingPool={}, ClassicNotUsed={}, ClassicVmMismatch={}, ClassicPoolNodeMismatch={}, VpcPrivateMissingSubnet={}, VpcPrivateNotUsed={}, VpcPrivateVmMismatch={}, VpcPublicMissingPool={}, VpcPublicNotUsed={}, VpcPublicVmMismatch={}, VpcPublicPoolNodeMismatch={}, VpcBindingMismatch={}",
                    counter.vmCount, counter.ipCount, counter.classicMissingPool, counter.classicNotUsed,
                    counter.classicVmMismatch, counter.classicPoolNodeMismatch, counter.vpcPrivateMissingSubnet, counter.vpcPrivateNotUsed,
                    counter.vpcPrivateVmMismatch, counter.vpcPublicMissingPool, counter.vpcPublicNotUsed,
                    counter.vpcPublicVmMismatch, counter.vpcPublicPoolNodeMismatch, counter.vpcBindingMismatch);
            if (detailCount[0] > VMHOST_AUDIT_DETAIL_LIMIT) {
                log.warn("[IppoolAudit] VM绑定异常明细过多，本次仅输出前{}条，剩余{}条请结合汇总排查",
                        VMHOST_AUDIT_DETAIL_LIMIT, detailCount[0] - VMHOST_AUDIT_DETAIL_LIMIT);
            }
        } else {
            log.info("[IppoolAudit] VM绑定巡检完成，未发现异常: VmCount={}, IpCount={}", counter.vmCount, counter.ipCount);
        }
    }

    private void auditOneVmhostIpBindings(Vmhost vmhost, VmhostAuditCounter counter, int[] detailCount) {
        if (vmhost == null) {
            return;
        }
        counter.vmCount++;
        List<String> ipList = getVmhostIpList(vmhost);
        counter.ipCount += ipList.size();
        if (isVpcVmhost(vmhost)) {
            auditVpcPrivateIpBindings(vmhost, ipList, counter, detailCount);
            auditVpcPublicIpBindings(vmhost, counter, detailCount);
            return;
        }
        auditClassicIpBindings(vmhost, ipList, counter, detailCount);
    }

    private void auditClassicIpBindings(Vmhost vmhost, List<String> ipList, VmhostAuditCounter counter,
                                        int[] detailCount) {
        for (String ip : ipList) {
            Ippool ippool = getBestIppoolForVmhostIp(vmhost, ip);
            if (ippool == null) {
                counter.classicMissingPool++;
                logVmhostAuditDetail(detailCount,
                        "[IppoolAudit] VM绑定异常: Type=VM记录IP未找到IP池记录, HostId={}, Hostname={}, VmId={}, NodeId={}, Ip={}",
                        vmhost.getId(), vmhost.getHostname(), vmhost.getVmid(), vmhost.getNodeid(), ip);
                continue;
            }
            if (!Objects.equals(ippool.getStatus(), 1)) {
                counter.classicNotUsed++;
                logVmhostAuditDetail(detailCount,
                        "[IppoolAudit] VM绑定异常: Type=VM记录IP未被占用, HostId={}, Hostname={}, VmId={}, NodeId={}, Ip={}, IppoolId={}, PoolNodeId={}, Status={}, BoundVmId={}",
                        vmhost.getId(), vmhost.getHostname(), vmhost.getVmid(), vmhost.getNodeid(), ip, ippool.getId(), ippool.getNodeId(),
                        ippool.getStatus(), ippool.getVmId());
            }
            if (!Objects.equals(ippool.getVmId(), vmhost.getVmid())) {
                counter.classicVmMismatch++;
                logVmhostAuditDetail(detailCount,
                        "[IppoolAudit] VM绑定异常: Type=VM记录IP绑定VMID不一致, HostId={}, Hostname={}, VmId={}, NodeId={}, Ip={}, IppoolId={}, PoolNodeId={}, Status={}, BoundVmId={}",
                        vmhost.getId(), vmhost.getHostname(), vmhost.getVmid(), vmhost.getNodeid(), ip, ippool.getId(), ippool.getNodeId(),
                        ippool.getStatus(), ippool.getVmId());
            }
            if (!Objects.equals(ippool.getNodeId(), vmhost.getNodeid())) {
                counter.classicPoolNodeMismatch++;
                logVmhostAuditDetail(detailCount,
                        "[IppoolAudit] VM绑定异常: Type=IP池节点与VM节点不一致, HostId={}, Hostname={}, VmId={}, NodeId={}, Ip={}, IppoolId={}, PoolId={}, PoolNodeId={}, Status={}, BoundVmId={}",
                        vmhost.getId(), vmhost.getHostname(), vmhost.getVmid(), vmhost.getNodeid(), ip,
                        ippool.getId(), ippool.getPoolId(), ippool.getNodeId(), ippool.getStatus(), ippool.getVmId());
            }
        }
    }

    private void auditVpcPrivateIpBindings(Vmhost vmhost, List<String> ipList, VmhostAuditCounter counter,
                                           int[] detailCount) {
        for (String ip : ipList) {
            Subnetpool subnetpool = getSubnetpoolByVmhostAndIp(vmhost, ip);
            if (subnetpool == null) {
                counter.vpcPrivateMissingSubnet++;
                logVmhostAuditDetail(detailCount,
                        "[IppoolAudit] VM绑定异常: Type=VPC私网IP未找到子网池记录, HostId={}, Hostname={}, VmId={}, NodeId={}, SubnetId={}, Ip={}",
                        vmhost.getId(), vmhost.getHostname(), vmhost.getVmid(), vmhost.getNodeid(), vmhost.getVpcSubnetId(), ip);
                continue;
            }
            if (!Objects.equals(subnetpool.getStatus(), 1)) {
                counter.vpcPrivateNotUsed++;
                logVmhostAuditDetail(detailCount,
                        "[IppoolAudit] VM绑定异常: Type=VPC私网IP未被占用, HostId={}, Hostname={}, VmId={}, NodeId={}, SubnetId={}, Ip={}, SubnetpoolId={}, Status={}, BoundVmId={}",
                        vmhost.getId(), vmhost.getHostname(), vmhost.getVmid(), vmhost.getNodeid(), vmhost.getVpcSubnetId(), ip,
                        subnetpool.getId(), subnetpool.getStatus(), subnetpool.getVmId());
            }
            if (!Objects.equals(subnetpool.getVmId(), vmhost.getVmid())) {
                counter.vpcPrivateVmMismatch++;
                logVmhostAuditDetail(detailCount,
                        "[IppoolAudit] VM绑定异常: Type=VPC私网IP绑定VMID不一致, HostId={}, Hostname={}, VmId={}, NodeId={}, SubnetId={}, Ip={}, SubnetpoolId={}, Status={}, BoundVmId={}",
                        vmhost.getId(), vmhost.getHostname(), vmhost.getVmid(), vmhost.getNodeid(), vmhost.getVpcSubnetId(), ip,
                        subnetpool.getId(), subnetpool.getStatus(), subnetpool.getVmId());
            }
        }
    }

    private void auditVpcPublicIpBindings(Vmhost vmhost, VmhostAuditCounter counter, int[] detailCount) {
        QueryWrapper<VpcIpBinding> bindingQueryWrapper = new QueryWrapper<>();
        bindingQueryWrapper.eq("host_id", vmhost.getId());
        bindingQueryWrapper.eq("status", 1);
        List<VpcIpBinding> bindingList = vpcIpBindingService.list(bindingQueryWrapper);
        for (VpcIpBinding binding : bindingList) {
            if (!Objects.equals(binding.getVmId(), vmhost.getVmid())
                    || !Objects.equals(binding.getNodeId(), vmhost.getNodeid())) {
                counter.vpcBindingMismatch++;
                logVmhostAuditDetail(detailCount,
                        "[IppoolAudit] VM绑定异常: Type=VPC公网绑定记录与VM不一致, HostId={}, Hostname={}, VmId={}, NodeId={}, BindingId={}, BindingVmId={}, BindingNodeId={}, PublicIp={}, PrivateIp={}",
                        vmhost.getId(), vmhost.getHostname(), vmhost.getVmid(), vmhost.getNodeid(), binding.getId(),
                        binding.getVmId(), binding.getNodeId(), binding.getPublicIp(), binding.getPrivateIp());
            }
            String publicIp = normalizeIpAddress(binding.getPublicIp());
            if (StringUtils.isBlank(publicIp)) {
                continue;
            }
            Ippool ippool = getBestIppoolForVpcBinding(vmhost, binding, publicIp);
            if (ippool == null) {
                counter.vpcPublicMissingPool++;
                logVmhostAuditDetail(detailCount,
                        "[IppoolAudit] VM绑定异常: Type=VPC公网IP未找到IP池记录, HostId={}, Hostname={}, VmId={}, NodeId={}, BindingId={}, PublicIp={}, PrivateIp={}",
                        vmhost.getId(), vmhost.getHostname(), vmhost.getVmid(), vmhost.getNodeid(), binding.getId(),
                        binding.getPublicIp(), binding.getPrivateIp());
                continue;
            }
            if (!Objects.equals(ippool.getStatus(), 1)) {
                counter.vpcPublicNotUsed++;
                logVmhostAuditDetail(detailCount,
                        "[IppoolAudit] VM绑定异常: Type=VPC公网IP未被占用, HostId={}, Hostname={}, VmId={}, NodeId={}, BindingId={}, PublicIp={}, IppoolId={}, PoolNodeId={}, Status={}, BoundVmId={}",
                        vmhost.getId(), vmhost.getHostname(), vmhost.getVmid(), vmhost.getNodeid(), binding.getId(),
                        publicIp, ippool.getId(), ippool.getNodeId(), ippool.getStatus(), ippool.getVmId());
            }
            if (!Objects.equals(ippool.getVmId(), vmhost.getVmid())) {
                counter.vpcPublicVmMismatch++;
                logVmhostAuditDetail(detailCount,
                        "[IppoolAudit] VM绑定异常: Type=VPC公网IP绑定VMID不一致, HostId={}, Hostname={}, VmId={}, NodeId={}, BindingId={}, PublicIp={}, IppoolId={}, PoolNodeId={}, Status={}, BoundVmId={}",
                        vmhost.getId(), vmhost.getHostname(), vmhost.getVmid(), vmhost.getNodeid(), binding.getId(),
                        publicIp, ippool.getId(), ippool.getNodeId(), ippool.getStatus(), ippool.getVmId());
            }
            if (!Objects.equals(ippool.getNodeId(), vmhost.getNodeid())) {
                counter.vpcPublicPoolNodeMismatch++;
                logVmhostAuditDetail(detailCount,
                        "[IppoolAudit] VM绑定异常: Type=VPC公网IP池节点与VM节点不一致, HostId={}, Hostname={}, VmId={}, NodeId={}, BindingId={}, PublicIp={}, IppoolId={}, PoolId={}, PoolNodeId={}, Status={}, BoundVmId={}",
                        vmhost.getId(), vmhost.getHostname(), vmhost.getVmid(), vmhost.getNodeid(), binding.getId(),
                        publicIp, ippool.getId(), ippool.getPoolId(), ippool.getNodeId(), ippool.getStatus(), ippool.getVmId());
            }
        }
    }

    private List<String> getVmhostIpList(Vmhost vmhost) {
        Set<String> ipSet = new LinkedHashSet<>();
        if (vmhost.getIpList() != null) {
            for (String ip : vmhost.getIpList()) {
                addNormalizedIp(ipSet, ip);
            }
        }
        for (String ip : CloudInitNetworkUtil.getIpList(vmhost.getIpConfig())) {
            addNormalizedIp(ipSet, ip);
        }
        return List.copyOf(ipSet);
    }

    private void addNormalizedIp(Set<String> ipSet, String ip) {
        String normalizedIp = normalizeIpAddress(ip);
        if (StringUtils.isNotBlank(normalizedIp)) {
            ipSet.add(normalizedIp);
        }
    }

    private String normalizeIpAddress(String ip) {
        if (StringUtils.isBlank(ip)) {
            return null;
        }
        String normalizedIp = ip.trim();
        if ("dhcp".equalsIgnoreCase(normalizedIp)) {
            return null;
        }
        int maskIndex = normalizedIp.indexOf('/');
        if (maskIndex > 0) {
            normalizedIp = normalizedIp.substring(0, maskIndex);
        }
        return normalizedIp;
    }

    private Ippool getBestIppoolForVmhostIp(Vmhost vmhost, String ip) {
        if (vmhost == null || StringUtils.isBlank(ip)) {
            return null;
        }
        Ippool boundIppool = getIppoolByIpVersionIpAndVmId(ip, vmhost.getVmid());
        if (boundIppool != null) {
            return boundIppool;
        }
        Ippool nodeIppool = getIppoolByNodeAndIp(vmhost.getNodeid(), ip);
        if (nodeIppool != null) {
            return nodeIppool;
        }
        return getIppoolByIpVersionAndIp(ip);
    }

    private Ippool getBestIppoolForVpcBinding(Vmhost vmhost, VpcIpBinding binding, String ip) {
        if (binding != null && binding.getIppoolId() != null) {
            Ippool ippool = ippoolService.getById(binding.getIppoolId());
            if (ippool != null && StringUtils.equals(normalizeIpAddress(ippool.getIp()), ip)) {
                return ippool;
            }
        }
        return getBestIppoolForVmhostIp(vmhost, ip);
    }

    private Ippool getIppoolByIpVersionIpAndVmId(String ip, Integer vmId) {
        if (StringUtils.isBlank(ip) || vmId == null) {
            return null;
        }
        QueryWrapper<Ippool> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("ip_version", ip.contains(":") ? 6 : 4);
        queryWrapper.eq("ip", ip);
        queryWrapper.eq("vm_id", vmId);
        queryWrapper.last("limit 1");
        return ippoolService.getOne(queryWrapper);
    }

    private Ippool getIppoolByNodeAndIp(Integer nodeId, String ip) {
        if (nodeId == null || StringUtils.isBlank(ip)) {
            return null;
        }
        QueryWrapper<Ippool> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("node_id", nodeId);
        queryWrapper.eq("ip_version", ip.contains(":") ? 6 : 4);
        queryWrapper.eq("ip", ip);
        queryWrapper.last("limit 1");
        return ippoolService.getOne(queryWrapper);
    }

    private Ippool getIppoolByIpVersionAndIp(String ip) {
        if (StringUtils.isBlank(ip)) {
            return null;
        }
        QueryWrapper<Ippool> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("ip_version", ip.contains(":") ? 6 : 4);
        queryWrapper.eq("ip", ip);
        queryWrapper.orderByDesc("status");
        queryWrapper.orderByAsc("id");
        queryWrapper.last("limit 1");
        return ippoolService.getOne(queryWrapper);
    }

    private Subnetpool getSubnetpoolByVmhostAndIp(Vmhost vmhost, String ip) {
        if (vmhost == null || vmhost.getNodeid() == null || StringUtils.isBlank(ip)) {
            return null;
        }
        QueryWrapper<Subnetpool> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("node_id", vmhost.getNodeid());
        queryWrapper.eq("ip", ip);
        if (vmhost.getVpcSubnetId() != null) {
            queryWrapper.eq("subnat_id", vmhost.getVpcSubnetId());
        }
        queryWrapper.last("limit 1");
        return subnetpoolService.getOne(queryWrapper);
    }

    private boolean isVpcVmhost(Vmhost vmhost) {
        return vmhost != null && NETWORK_TYPE_VPC.equalsIgnoreCase(vmhost.getNetworkType());
    }

    private void logVmhostAuditDetail(int[] detailCount, String template, Object... args) {
        if (detailCount[0] < VMHOST_AUDIT_DETAIL_LIMIT) {
            log.warn(template, args);
        }
        detailCount[0]++;
    }

    private static class VmhostAuditCounter {
        private int vmCount;
        private int ipCount;
        private int classicMissingPool;
        private int classicNotUsed;
        private int classicVmMismatch;
        private int classicPoolNodeMismatch;
        private int vpcPrivateMissingSubnet;
        private int vpcPrivateNotUsed;
        private int vpcPrivateVmMismatch;
        private int vpcPublicMissingPool;
        private int vpcPublicNotUsed;
        private int vpcPublicVmMismatch;
        private int vpcPublicPoolNodeMismatch;
        private int vpcBindingMismatch;

        private boolean hasError() {
            return classicMissingPool > 0 || classicNotUsed > 0 || classicVmMismatch > 0 || classicPoolNodeMismatch > 0
                    || vpcPrivateMissingSubnet > 0 || vpcPrivateNotUsed > 0 || vpcPrivateVmMismatch > 0
                    || vpcPublicMissingPool > 0 || vpcPublicNotUsed > 0 || vpcPublicVmMismatch > 0 || vpcPublicPoolNodeMismatch > 0
                    || vpcBindingMismatch > 0;
        }
    }
}
