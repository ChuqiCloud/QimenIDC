package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.common.TimedLock;
import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.common.VmCreateLock;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.dto.VmParams;
import com.chuqiyun.proxmoxveams.entity.*;
import com.chuqiyun.proxmoxveams.service.*;
import com.chuqiyun.proxmoxveams.utils.*;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import static com.chuqiyun.proxmoxveams.constant.TaskType.CREATE_VM;

/**
 * @author mryunqi
 * @date 2023/8/6
 */
@Service("createVmService")
public class CreateVmServiceImpl implements CreateVmService {
    private static final String DEFAULT_CREATE_VM_STORAGE = "local-lvm";
    private static final String NETWORK_TYPE_CLASSIC = "classic";
    private static final String NETWORK_TYPE_VPC = "vpc";
    private static final int IP_VERSION_4 = 4;
    private static final int IP_VERSION_6 = 6;

    @Resource
    private MasterService masterService;
    @Resource
    private TaskService taskService;
    @Resource
    private IpstatusService ipstatusService;
    @Resource
    private IppoolService ippoolService;
    @Resource
    private OsService osService;
    @Resource
    private ModelgroupService modelgroupService;
    @Resource
    private CpuinfoService cpuinfoService;
    @Resource
    private ConfiguretemplateService configuretemplateService;
    @Resource
    private SmbiosService smbiosService;
    @Resource
    private VmhostService vmhostService;
    @Resource
    private ConfigService configService;
    @Resource
    private SubnetpoolService subnetpoolService;
    @Resource
    private SubnetService subnetService;
    @Resource
    private VmInitScriptBusinessService vmInitScriptBusinessService;
    /**
     * 创建PVE虚拟机
     *
     */
    @Override
    public UnifiedResultDto<Object> createPveVmToParams(VmParams vmParams, boolean isApi){

        if (!FlowTypeUtil.isValid(vmParams.getFlowType())) {
            return invalidParam("flowType只能是in、out或in+out");
        }
        vmParams.setFlowType(FlowTypeUtil.normalize(vmParams.getFlowType()));
        if (vmParams.getHostname() != null && !vmParams.getHostname().equals("")) {
            Vmhost vmhost = vmhostService.getVmhostByNameOne(vmParams.getHostname());
            if (vmhost != null) {
                return new UnifiedResultDto<>(UnifiedResultCode.ERROR_HOSTNAME_IS_EXIST, null);
            }
        }
        // 判断nodeId是否为空
        if (vmParams.getNodeid() == null) {
            return invalidParam("nodeid不能为空");
        }
        int nodeId = vmParams.getNodeid();
        Master node = masterService.getById(nodeId);
        if (ModUtil.isNull(node)) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NODE_NOT_EXIST, null);
        }
        // 判断节点是否可用
        if (node.getStatus() >= 1) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NODE_NOT_AVAILABLE, null);
        }
        // 如果configureTemplateId不为空且大于0
        if (vmParams.getConfigureTemplateId() != null && vmParams.getConfigureTemplateId() > 0) {
            Configuretemplate configuretemplate = configuretemplateService.selectConfiguretemplateById(vmParams.getConfigureTemplateId());
            if (ModUtil.isNull(configuretemplate)) {
                return new UnifiedResultDto<>(UnifiedResultCode.ERROR_CONFIGURE_TEMPLATE_NOT_EXIST, null);
            }
            vmParams.setCpu(configuretemplate.getCpu());
            vmParams.setCores(configuretemplate.getCores());
            vmParams.setSockets(configuretemplate.getSockets());
            vmParams.setThreads(configuretemplate.getThreads());
            vmParams.setMemory(configuretemplate.getMemory());
            // 判断nested是否为空
            if (configuretemplate.getNested() == null) {
                vmParams.setNested(false);
            } else {
                vmParams.setNested(configuretemplate.getNested() == 1);
            }
            // 判断Devirtualization是否为空
            if (configuretemplate.getDevirtualization() == null) {
                vmParams.setDevirtualization(false);
            } else {
                vmParams.setDevirtualization(configuretemplate.getDevirtualization() == 1);
            }
            // 判断Kvm是否为空
            if (configuretemplate.getKvm() == null) {
                vmParams.setKvm(true);
            } else {
                vmParams.setKvm(configuretemplate.getKvm() == 1);
            }
            vmParams.setModelGroup(configuretemplate.getModelGroup());
            vmParams.setCpuModel(configuretemplate.getCpuModel());
            vmParams.setCpuUnits(configuretemplate.getCpuUnits());
            vmParams.setBwlimit(configuretemplate.getBwlimit());
            vmParams.setArch(configuretemplate.getArch());
            vmParams.setAcpi(configuretemplate.getAcpi());
            vmParams.setStorage(configuretemplate.getStorage());
            vmParams.setSystemDiskSize(configuretemplate.getSystemDiskSize());
            // 转换Map为HashMap
            HashMap<Object, Object> map = new HashMap<>();
            if (configuretemplate.getDataDisk() != null) {
                map.putAll(configuretemplate.getDataDisk());
            }
            vmParams.setDataDisk(map);
            vmParams.setBandwidth(configuretemplate.getBandwidth());
            vmParams.setOnBoot(configuretemplate.getOnBoot());
        }
        // 判断带宽是否为空
        if (vmParams.getBandwidth() == null) {
            vmParams.setBandwidth(1);
        }
        // 判断nested是否为空
        if (vmParams.getNested() == null) {
            vmParams.setNested(false);
        }
        // 判断sockets是否为空
        if (vmParams.getSockets() == null) {
            vmParams.setSockets(1);
        }
        // 小于1时使用默认值
        else if (vmParams.getSockets() < 1) {
            vmParams.setSockets(1);
        }
        // 判断threads是否为空
        if (vmParams.getThreads() == null) {
            vmParams.setThreads(1);
        }
        // 小于1时使用默认值
        else if (vmParams.getThreads() < 1) {
            vmParams.setThreads(1);
        }
        // 判断cores是否为空
        if (vmParams.getCores() == null) {
            vmParams.setCores(1);
        }
        // 小于1时使用默认值
        else if (vmParams.getCores() < 1) {
            vmParams.setCores(1);
        }
        // 判断cpu是否为空
        if (vmParams.getCpu() == null) {
            vmParams.setCpu("kvm64");
        }
        // 判断devirtualization是否为空
        if (vmParams.getDevirtualization() == null) {
            vmParams.setDevirtualization(false);
        }
        // 判断kvm是否为空
        if (vmParams.getKvm() == null) {
            vmParams.setKvm(true);
        }
        // 判断cpu是否支持
        if (!VmUtil.isCpuTypeExist(vmParams.getCpu())) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_CPU_TYPE_NOT_EXIST, null);
        }
        // 如果开启了nested，cpu必须为host或max
        if (vmParams.getNested() && !"host".equals(vmParams.getCpu()) && !"max".equals(vmParams.getCpu())) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_CPU_TYPE_NOT_SUPPORT_NESTED, null);
        }
        // 判断cpuUnits是否为空
        if (vmParams.getCpuUnits() == null) {
            vmParams.setCpuUnits(1024);
        }
        else if (vmParams.getCpuUnits() >= 100) {
            vmParams.setCpuUnits(1024*10);
        }
        else if (vmParams.getCpuUnits() < 1) {
            vmParams.setCpuUnits(1024);
        }
        else {
            vmParams.setCpuUnits(vmParams.getCpuUnits()*1024);
        }
        // 判断bwlimit是否为空
        if (vmParams.getBwlimit() == null) {
            vmParams.setBwlimit(configService.getBwlimit());
        }else {
            // mb/s转换为kb/s
            vmParams.setBwlimit(vmParams.getBwlimit()*1024);
        }
        // 判断arch是否为空
        if (vmParams.getArch() == null) {
            vmParams.setArch("x86_64");
        }
        // 判断arch是否支持
        else if (!VmUtil.isArchExist(vmParams.getArch())) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_ARCHITECTURE_NOT_EXIST, null);
        }
        // 判断acpi是否为空 0:禁用 1:启用
        if (vmParams.getAcpi() == null) {
            vmParams.setAcpi(1);
        }
        // 判断acpi是否为0或1
        else if (vmParams.getAcpi() != 0 && vmParams.getAcpi() != 1) {
            vmParams.setAcpi(1);
        }
        // 判断memory是否为空
        if (vmParams.getMemory() == null) {
            vmParams.setMemory(512);
        }
        // 如果storage为空或为auto，则使用节点自动选择的存储；节点未计算出存储时使用local-lvm
        vmParams.setStorage(getCreateVmStorage(vmParams.getStorage(), node));

        // 判断onBoot是否为空
        if (vmParams.getOnBoot() == null) {
            vmParams.setOnBoot(0);
        }
        // 判断ifNat是否为空
        if (vmParams.getIfnat() == null) {
            vmParams.setIfnat(0);
        }
        normalizeNetworkType(vmParams);
        // 设置网桥
        if (isVpcNetwork(vmParams)) {
            Subnet subnet = resolveVpcSubnet(vmParams);
            if (subnet == null) {
                return invalidParam(buildVpcSubnetInvalidMessage(vmParams));
            }
            vmParams.setVpcSubnetId(subnet.getId());
            vmParams.setBridge(subnet.getVnet());
            vmParams.setIfnat(0);
        } else if (vmParams.getBridge() == null) {
            if(vmParams.getIfnat() == 1 && node.getNaton() == 1) // NAT网络
            {
                vmParams.setBridge(node.getNatbridge());
            } else {
                vmParams.setBridge("vmbr0");
            }
        }

        // 判断os、template、iso是否同时为空，至少需要一个不为空
        if (vmParams.getOs() == null && vmParams.getTemplate() == null && vmParams.getIso() == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_IMAGE_NOT_NULL, null);
        }
        Os os = osService.isExistOs(vmParams.getOs());
        // 判断镜像是否存在
        if (os == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_CLOUD_IMAGE_NOT_EXIST, null);
        }
        if (!osService.isNodeOsDownloaded(os, nodeId)) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_CLOUD_IMAGE_NOT_AVAILABLE, null);
        }
        vmParams.setOsName(os.getName()); // osName统一保存镜像名称
        vmParams.setOs(os.getFileName());
        // 判断osType是否为空
        if (vmParams.getOsType() == null) {
            vmParams.setOsType(os.getType());
        }
        // 临时修复debian/ubuntu系统类型
        if(Objects.equals(vmParams.getOsType(), "debian") || Objects.equals(vmParams.getOsType(), "ubuntu")){
            vmParams.setOsType("linux");
        }
        // 判断osType是否支持
        if (!VmUtil.isOsTypeExist(vmParams.getOsType())) {
            vmParams.setOsType("other");
        }
        // 判断username是否为空
        if (vmParams.getUsername() == null) {
            if (os.getOsType().equals("windows")){
                vmParams.setUsername("administrator");
            }
            else{
                vmParams.setUsername("root");
            }
        }
        // 判断系统盘大小是否为空
        if (vmParams.getSystemDiskSize() == null) {
            if (os.getType().equals("windows")){
                vmParams.setSystemDiskSize(configService.getWinSystemDiskSize());
            }
            else{
                vmParams.setSystemDiskSize(configService.getLinuxSystemDiskSize());
            }
        }
        // 判断password是否为空
        if (vmParams.getPassword() == null) {
            // 生成随机密码
            vmParams.setPassword(VmUtil.generatePassword());
        }
        List<Integer> initScriptIds = vmInitScriptBusinessService.normalizeScriptIds(vmParams.getInitScriptId(), vmParams.getInitScriptIds());
        UnifiedResultDto<Object> initScriptCheck = vmInitScriptBusinessService.validateScripts(initScriptIds);
        if (initScriptCheck.getResultCode().getCode() != UnifiedResultCode.SUCCESS.getCode()) {
            return initScriptCheck;
        }
        vmParams.setInitScriptIds(initScriptIds);
        vmParams.setInitScriptId(initScriptIds.isEmpty() ? null : initScriptIds.get(0));
        // 获取默认IPv4地址池，仅用于DNS默认值；实际分配以ippool实时空闲状态为准
        HashMap<String, String> ipConfigMap = vmParams.getIpConfig() == null ? new HashMap<>() : vmParams.getIpConfig();
        List<Subnetpool> reservedVpcSubnetpools = new ArrayList<>();
        List<String> selectedIpList = isVpcNetwork(vmParams) ? CloudInitNetworkUtil.getIpList(ipConfigMap) : CloudInitNetworkUtil.getIpv4List(ipConfigMap);
        List<String> selectedIpv6List = CloudInitNetworkUtil.getIpv6List(ipConfigMap);
        Set<String> selectedIpSet = new LinkedHashSet<>(selectedIpList);
        Set<String> selectedIpv6Set = new LinkedHashSet<>(selectedIpv6List);
        Integer requestedIpv4Count = getRequestedIpCount(vmParams.getIpv4num(), selectedIpSet.size(), getIpConfigCount(ipConfigMap), true);
        Integer requestedIpv6Count = getRequestedIpCount(vmParams.getIpv6num(), selectedIpv6Set.size(), 0, false);
        if (requestedIpv4Count == null || requestedIpv6Count == null) {
            return invalidParam("ipv4num/ipv6num涓嶈兘灏忎簬0");
        }
        if (selectedIpSet.size() > requestedIpv4Count) {
            return invalidParam("ipConfig中IPv4数量不能大于ipv4num");
        }
        if (selectedIpv6Set.size() > requestedIpv6Count) {
            return invalidParam("ipConfig中IPv6数量不能大于ipv6num");
        }
        if (requestedIpv4Count + requestedIpv6Count <= 0) {
            return invalidParam("ipv4num和ipv6num不能同时为0");
        }
        if (isVpcNetwork(vmParams) && (requestedIpv6Count > 0 || !selectedIpv6Set.isEmpty())) {
            return invalidParam("VPC网络暂不支持IPv6，请使用经典网络");
        }
        if (!isVpcNetwork(vmParams) && requestedIpv4Count == 0 && Objects.equals(vmParams.getIfnat(), 1)) {
            return invalidParam("纯IPv6虚拟机不支持NAT");
        }
        Ipstatus ipPool = requestedIpv4Count > 0
                ? getDefaultCreateVmIpPool(nodeId, node, vmParams.getIfnat(), IP_VERSION_4)
                : getDefaultCreateVmIpPool(nodeId, node, vmParams.getIfnat(), IP_VERSION_6);
        if (isVpcNetwork(vmParams) && selectedIpList.size() != selectedIpSet.size()) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NO_AVAILABLE_IPV4, null,
                    "VPC子网中存在重复私网IP: nodeid=" + nodeId + ", vpcSubnetId=" + vmParams.getVpcSubnetId());
        }
        if (isVpcNetwork(vmParams) && !reserveSelectedVpcIpsForCreateVm(nodeId, vmParams.getVpcSubnetId(), selectedIpSet, reservedVpcSubnetpools)) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NO_AVAILABLE_IPV4, null,
                    "VPC子网中指定的私网IP不可用: nodeid=" + nodeId + ", vpcSubnetId=" + vmParams.getVpcSubnetId());
        }
        if (isVpcNetwork(vmParams)) {
            while (selectedIpSet.size() < requestedIpv4Count) {
                int configIndex = findIpConfigSlot(ipConfigMap, false);
                Subnetpool subnetpool = reserveOneFreeVpcIpForCreateVm(nodeId, vmParams.getVpcSubnetId(), selectedIpSet, reservedVpcSubnetpools);
                if (subnetpool == null) {
                    releaseReservedVpcIpsForCreateVm(reservedVpcSubnetpools);
                    return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NO_AVAILABLE_IPV4, null,
                            "VPC没有可用私网IP: nodeid=" + nodeId + ", vpcSubnetId=" + vmParams.getVpcSubnetId());
                }
                appendIpConfigItem(ipConfigMap, configIndex, "ip=" + subnetpool.getIp() + "/" + subnetpool.getMask() + ",gw=" + subnetpool.getGateway());
                selectedIpSet.add(subnetpool.getIp());
                if (vmParams.getDns1() == null) {
                    vmParams.setDns1(subnetpool.getDns());
                }
            }
        } else {
            while (selectedIpSet.size() < requestedIpv4Count) {
                Ippool ipEntity = getOneFreeIpForCreateVm(nodeId, vmParams.getIfnat(), node.getNatippool(), selectedIpSet, IP_VERSION_4);
                Ipstatus currentIpPool = appendClassicIpConfig(ipConfigMap, ipEntity, selectedIpSet, false);
                if (currentIpPool == null) {
                    return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NO_AVAILABLE_IPV4, null);
                }
                if (ipPool == null) {
                    ipPool = currentIpPool;
                }
            }
            while (selectedIpv6Set.size() < requestedIpv6Count) {
                Ippool ipv6Entity = getOneFreeIpForCreateVm(nodeId, vmParams.getIfnat(), node.getNatippool(), selectedIpv6Set, IP_VERSION_6);
                Ipstatus currentIpv6Pool = appendClassicIpConfig(ipConfigMap, ipv6Entity, selectedIpv6Set, true);
                if (currentIpv6Pool == null) {
                    return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NO_AVAILABLE_IPV6, null);
                }
                if (ipPool == null) {
                    ipPool = currentIpv6Pool;
                }
            }
        }
        int ipCount = 0;
        for (int i = 1; i <= ipCount; i++) {
            String ipConfig = ipConfigMap.get(String.valueOf(i));
            if (CloudInitNetworkUtil.getIpFromCloudInitConfig(ipConfig) != null) {
                if (!isVpcNetwork(vmParams)) {
                    ipConfigMap.put(String.valueOf(i), ensureClassicIpv6Config(ipConfig, nodeId, vmParams, node, selectedIpv6Set));
                }
                continue;
            }
            if (isVpcNetwork(vmParams)) {
                Subnetpool subnetpool = reserveOneFreeVpcIpForCreateVm(nodeId, vmParams.getVpcSubnetId(), selectedIpSet, reservedVpcSubnetpools);
                if (subnetpool == null) {
                    releaseReservedVpcIpsForCreateVm(reservedVpcSubnetpools);
                    return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NO_AVAILABLE_IPV4, null,
                            "VPC子网没有可用私网IP: nodeid=" + nodeId + ", vpcSubnetId=" + vmParams.getVpcSubnetId());
                }
                ipConfigMap.put(String.valueOf(i), "ip=" + subnetpool.getIp() + "/" + subnetpool.getMask() + ",gw=" + subnetpool.getGateway());
                selectedIpSet.add(subnetpool.getIp());
                if (vmParams.getDns1() == null) {
                    vmParams.setDns1(subnetpool.getDns());
                }
            } else {
                Ippool ipEntity = getOneFreeIpForCreateVm(nodeId, vmParams.getIfnat(), node.getNatippool(), selectedIpSet, IP_VERSION_4);
                if (ipEntity == null) {
                    return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NO_AVAILABLE_IPV4, null);
                }
                Ipstatus currentIpPool = ipstatusService.getById(ipEntity.getPoolId());
                if (currentIpPool == null) {
                    return new UnifiedResultDto<>(UnifiedResultCode.ERROR_IP_POOL_NOT_EXIST, null);
                }
                String configValue = "ip=" + ipEntity.getIp() + "/" + currentIpPool.getMask() + ",gw=" + ipEntity.getGateway();
                configValue = ensureClassicIpv6Config(configValue, nodeId, vmParams, node, selectedIpv6Set);
                ipConfigMap.put(String.valueOf(i), configValue);
                selectedIpSet.add(ipEntity.getIp());
                if (ipPool == null) {
                    ipPool = currentIpPool;
                }
            }
        }
        vmParams.setIpConfig(ipConfigMap);
        List<String> ipList = CloudInitNetworkUtil.getIpList(ipConfigMap);
        if (ipList.isEmpty()) {
            releaseReservedVpcIpsForCreateVm(reservedVpcSubnetpools);
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NO_AVAILABLE_IPV4, null);
        }
        if (vmParams.getHostname() == null) {
            vmParams.setHostname(ModUtil.ipReplace(ipList.get(0)));
        } else if (ModUtil.isChinese(vmParams.getHostname())) {
            releaseReservedVpcIpsForCreateVm(reservedVpcSubnetpools);
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_HOSTNAME_NOT_CHINESE, null);
        }
        vmParams.setIpList(ipList);
        if (isVpcNetwork(vmParams)) {
            List<String> publicIpList = getVpcPublicIpListForCreateVm(nodeId, node, vmParams, ipList.size());
            if (publicIpList == null || publicIpList.size() < ipList.size()) {
                releaseReservedVpcIpsForCreateVm(reservedVpcSubnetpools);
                return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NO_AVAILABLE_IPV4, null,
                    "公网IP池没有足够可用IP: nodeid=" + nodeId + ", publicIpPoolId=" + vmParams.getPublicIpPoolId());
            }
            vmParams.setPublicIpList(publicIpList);
        }
        // 设置dns
        if (vmParams.getDns1() == null && ipPool != null) {
            vmParams.setDns1(ipPool.getDns1());
        }
        // 判断natnum是否为空
        if (vmParams.getNatnum() == null) {
            vmParams.setNatnum(0);
        }
        if (vmParams.getExtraFlowLimit() == null)
        {
            vmParams.setExtraFlowLimit(0L);
        }
        if (vmParams.getResetFlowTime() == null)
        {
            vmParams.setResetFlowTime(0);
        }
        if (vmParams.getOutFlow() == null)
        {
            vmParams.setOutFlow(0);
        }
        // 转换vmParams为HashMap
        HashMap<Object, Object> vmParamsMap;
        try {
            vmParamsMap = EntityHashMapConverterUtil.convertToHashMap(vmParams);
        } catch (IllegalAccessException e) {
            releaseReservedVpcIpsForCreateVm(reservedVpcSubnetpools);
            UnifiedLogger.log(UnifiedLogger.LogType.TASK_CREATE_VM, "创建虚拟机任务参数转换失败");
            e.printStackTrace();
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_CREATE_VM_FAILED, null);
        }
        // 创建虚拟机任务
        Task task = new Task();
        task.setStatus(0);
        task.setType(CREATE_VM);
        task.setParams(vmParamsMap);
        task.setCreateDate(System.currentTimeMillis());
        //
        if (taskService.insertTask(task)){
            UnifiedLogger.log(UnifiedLogger.LogType.TASK_CREATE_VM, "创建虚拟机任务 NodeID="+nodeId+",OsType="+vmParams.getOsType()+
                    ",Sockets="+vmParams.getSockets()+",Cores="+vmParams.getCores()+",Memory="+vmParams.getMemory());
            // 如果isApi为true，则循环等待任务完成
            if (isApi) {
                int count = 0;
                while (true) {
                    // 如果超过30秒还没有完成，则返回失败
                    if (count >= 300) {
                        return new UnifiedResultDto<>(UnifiedResultCode.ERROR_CREATE_VM_FAILED, null);
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Task task1 = taskService.getById(task.getId());
                    // 等于0表示任务还未开始
                    if (task1.getStatus() == 0) {
                        count++;
                        continue;
                    }
                    // 等于1表示任务正在进行
                    else if (task1.getStatus() == 1) {
                        count++;
                        continue;
                    }
                    // 等于4表示任务成功
                    else if (task1.getStatus() == 4) {
                        // 设置虚拟机ID
                        vmParams.setVmid(task1.getVmid());
                        // 设置虚拟机hostId
                        vmParams.setHostid(task1.getHostid());
                        vmParams.setIpData(VmUtil.splitIpAddress(vmParams.getIpConfig()));
                        return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, vmParams);
                    }
                    // 等于2表示任务完成
                    else if (task1.getStatus() == 2) {
                        // 设置虚拟机ID
                        vmParams.setVmid(task1.getVmid());
                        // 设置虚拟机hostId
                        vmParams.setHostid(task1.getHostid());
                        /*HashMap<Object, Object> vmParamsMapResult;
                        try {
                            vmParamsMapResult = EntityHashMapConverterUtil.convertToHashMap(vmParams);
                        } catch (IllegalAccessException e) {
                            UnifiedLogger.log(UnifiedLogger.LogType.TASK_CREATE_VM, "返回参数转换失败");
                            e.printStackTrace();
                            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_CREATE_VM_FAILED, null);
                        }*/

                        /*vmParamsMapResult.put("ipData", VmUtil.splitIpAddress(vmParams.getIpConfig()));*/
                        vmParams.setIpData(VmUtil.splitIpAddress(vmParams.getIpConfig()));
                        return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, vmParams);
                    }
                    // 等于3表示任务失败
                    else if (task1.getStatus() == 3) {
                        return new UnifiedResultDto<>(UnifiedResultCode.ERROR_CREATE_VM_FAILED, null);
                    }
                    else {
                        return new UnifiedResultDto<>(UnifiedResultCode.ERROR_CREATE_VM_FAILED, null);
                    }
                }
            }else {
                return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, vmParams);
            }
        }
        releaseReservedVpcIpsForCreateVm(reservedVpcSubnetpools);
        return new UnifiedResultDto<>(UnifiedResultCode.ERROR_CREATE_VM_FAILED, null);
    }

    /**
     * @Author: mryunqi
     * @Description: 创建虚拟机
     * @DateTime: 2023/6/21 23:41
     */
    @Override
    public Integer createPveVm(VmParams vmParams, Integer vmid) {
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        Master node = masterService.getById(vmParams.getNodeid());
        if (node == null) {
            UnifiedLogger.error(UnifiedLogger.LogType.TASK_CREATE_VM, "创建基础虚拟机失败，节点不存在 NodeID={}", vmParams.getNodeid());
            return 0;
        }
        vmParams.setStorage(getCreateVmStorage(vmParams.getStorage(), node));
        // 创建虚拟机可选参数
        HashMap<String, Object> param = new HashMap<>();
        int vmId = vmid;
        param.put("vmid", vmId);
        param.put("name", vmParams.getHostname());
        // 设置CPU
        param.put("cpu", vmParams.getCpu());
        // 设置CPU插槽数
        param.put("sockets", vmParams.getSockets());
        // 设置CPU核心数
        param.put("cores", vmParams.getCores());

        // 如果modelGroup为空
        if (vmParams.getModelGroup() == null) {
            if (vmParams.getCpuModel() != null) {
                Cpuinfo cpuinfo = cpuinfoService.getById(vmParams.getCpuModel());
                vmParams.setArgs(cpuinfoService.cpuinfoToString(cpuinfo));
                VmUtil.getArgs(vmParams, param);
            }

        } else {
            Modelgroup modelgroup = modelgroupService.getById(vmParams.getModelGroup());
            vmParams.setArgs(modelgroup.getArgs());
            VmUtil.getArgsByModelGroup(vmParams, param);
        }
        // 设置kvm
        param.put("kvm", vmParams.getKvm());

        String primaryIpConfig = CloudInitNetworkUtil.getPrimaryIpConfig(vmParams.getIpConfig());
        if (primaryIpConfig != null) {
            param.put("ipconfig0", primaryIpConfig);
        }
        //param.put("ipconfig0", "ip=23.94.247.39/28,gw=23.94.247.33");
        // 设置DNS
        param.put("nameserver", vmParams.getDns1());
        // 设置虚拟机ostype
        param.put("ostype", OsTypeUtil.getOsType(vmParams.getOs(),vmParams.getOsType()));
        // 开机启动
        param.put("onboot", vmParams.getOnBoot());
        // 设置bwlimit
        param.put("bwlimit", vmParams.getBwlimit());
        // 设置内存
        param.put("memory", vmParams.getMemory());
        // 设置arch
        param.put("arch", vmParams.getArch());
        // 设置acpi
        param.put("acpi", vmParams.getAcpi());
        // 启用QEMU Agent
        param.put("agent", 1);
        // 设置虚拟机citype
        if ("windows".equals(vmParams.getOsType())){
            param.put("citype","configdrive2");
        }
        if ("linux".equals(vmParams.getOsType())){
            param.put("citype","nocloud");
        }
        // 设置虚拟机账号
        param.put("ciuser",vmParams.getUsername());
        // 设置虚拟机密码
        param.put("cipassword",vmParams.getPassword());
        // 设置cloud-init
        param.put("ide2", vmParams.getStorage() + ":cloudinit");
        // 设置带宽
        double bandWidthValue = vmParams.getBandwidth() / 8.0;
        String bandWidth = String.format(Locale.US, "%.2f", bandWidthValue);
        boolean multiIp = CloudInitNetworkUtil.getIpAddressCount(vmParams.getIpConfig()) > 1;
        boolean linuxMultiIp = multiIp && !"windows".equals(vmParams.getOsType());
        String macAddress = linuxMultiIp ? CloudInitNetworkUtil.buildStableMacAddress(vmParams.getNodeid(), vmId) : null;
        param.put("net0", buildNet0Config(node, vmParams, bandWidth, macAddress));
        if (linuxMultiIp) {
            try {
                CloudInitNetworkUtil.uploadSingleNicNetworkSnippet(node, vmId, vmParams.getIpConfig(), getNameservers(vmParams), macAddress);
            } catch (Exception e) {
                UnifiedLogger.error(UnifiedLogger.LogType.TASK_CREATE_VM, "写入单网卡多IP cloud-init 配置失败: vmid=" + vmId);
                e.printStackTrace();
                return 0;
            }
            param.put("cicustom", "network=" + CloudInitNetworkUtil.getNetworkSnippetVolume(vmId));
        }

        // 获取cookie
        HashMap<String, String> authentications = masterService.getMasterCookieMap(vmParams.getNodeid());
        JSONObject jsonObject =  proxmoxApiUtil.postNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu", param);
        if (jsonObject.containsKey("data")){
            syncCreateVmFirewallProtection(node, authentications, vmParams, vmId, proxmoxApiUtil);
            return vmId;
        }else {
            return 0;
        }
    }

    /**
     * @Author: 星禾
     * @Description: 获取创建虚拟机时可用的存储名称
     * @DateTime: 2026/6/6 20:24
     */
    private String getCreateVmStorage(String storage, Master node) {
        String normalizedStorage = normalizeStorageText(storage);
        if (!isAutoStorage(normalizedStorage)) {
            return normalizedStorage;
        }
        String autoStorage = node == null ? null : normalizeStorageText(node.getAutoStorage());
        if (!isAutoStorage(autoStorage)) {
            return autoStorage;
        }
        return DEFAULT_CREATE_VM_STORAGE;
    }

    private boolean isAutoStorage(String storage) {
        return storage == null || storage.isEmpty() || "auto".equalsIgnoreCase(storage);
    }

    private String normalizeStorageText(String storage) {
        return storage == null ? null : storage.trim();
    }

    private int getIpConfigCount(HashMap<String, String> ipConfigMap) {
        if (ipConfigMap == null || ipConfigMap.isEmpty()) {
            return 1;
        }
        int count = ipConfigMap.size();
        for (String key : ipConfigMap.keySet()) {
            try {
                count = Math.max(count, Integer.parseInt(key));
            } catch (NumberFormatException ignored) {
            }
        }
        return count;
    }

    private Integer getRequestedIpCount(Integer requestedCount, int selectedCount, int defaultCount, boolean useDefaultCount) {
        if (requestedCount != null) {
            return requestedCount < 0 ? null : requestedCount;
        }
        return Math.max(selectedCount, useDefaultCount ? defaultCount : 0);
    }

    private Ipstatus getDefaultCreateVmIpPool(Integer nodeId, Master node, Integer ifnat) {
        return getDefaultCreateVmIpPool(nodeId, node, ifnat, IP_VERSION_4);
    }

    private Ipstatus getDefaultCreateVmIpPool(Integer nodeId, Master node, Integer ifnat, Integer ipVersion) {
        QueryWrapper<Ipstatus> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("nodeid", nodeId);
        queryWrapper.eq("ip_version", ipVersion);
        if (Objects.equals(ifnat, 1) && Objects.equals(ipVersion, IP_VERSION_4)) {
            if (node != null && node.getNatippool() != null) {
                queryWrapper.eq("id", node.getNatippool());
            }
        } else if (node != null && node.getNatippool() != null) {
            queryWrapper.ne("id", node.getNatippool());
        }
        queryWrapper.orderByDesc("available");
        queryWrapper.last("limit 1");
        return ipstatusService.getOne(queryWrapper);
    }

    private Ippool getOneFreeIpForCreateVm(Integer nodeId, Integer ifnat, Integer natippool, Set<String> excludeIpSet) {
        return getOneFreeIpForCreateVm(nodeId, ifnat, natippool, excludeIpSet, IP_VERSION_4);
    }

    private Ippool getOneFreeIpForCreateVm(Integer nodeId, Integer ifnat, Integer natippool, Set<String> excludeIpSet, Integer ipVersion) {
        QueryWrapper<Ippool> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("node_id", nodeId);
        queryWrapper.eq("status", 0);
        queryWrapper.eq("ip_version", ipVersion);
        if (Objects.equals(ifnat, 1) && Objects.equals(ipVersion, IP_VERSION_4) && natippool != null) {
            queryWrapper.eq("pool_id", natippool);
        } else if (!Objects.equals(ifnat, 1) && natippool != null) {
            queryWrapper.ne("pool_id", natippool);
        } else if (Objects.equals(ipVersion, IP_VERSION_6) && natippool != null) {
            queryWrapper.ne("pool_id", natippool);
        }
        if (excludeIpSet != null && !excludeIpSet.isEmpty()) {
            queryWrapper.notIn("ip", excludeIpSet);
        }
        queryWrapper.orderByAsc("id");
        queryWrapper.last("limit 1");
        return ippoolService.getOne(queryWrapper);
    }

    private Ipstatus appendClassicIpConfig(HashMap<String, String> ipConfigMap, Ippool ipEntity, Set<String> selectedIpSet, boolean ipv6) {
        if (ipEntity == null) {
            return null;
        }
        Ipstatus currentIpPool = ipstatusService.getById(ipEntity.getPoolId());
        if (currentIpPool == null || currentIpPool.getMask() == null) {
            return null;
        }
        int configIndex = findIpConfigSlot(ipConfigMap, ipv6);
        Integer prefix = getClassicIpPrefix(ipEntity, currentIpPool);
        if (prefix == null) {
            return null;
        }
        String configValue = (ipv6 ? "ip6=" : "ip=") + ipEntity.getIp() + "/" + prefix
                + (ipv6 ? ",gw6=" : ",gw=") + ipEntity.getGateway();
        appendIpConfigItem(ipConfigMap, configIndex, configValue);
        selectedIpSet.add(ipEntity.getIp());
        return currentIpPool;
    }

    private int findIpConfigSlot(HashMap<String, String> ipConfigMap, boolean ipv6) {
        int count = getIpConfigCount(ipConfigMap);
        for (int i = 1; i <= count; i++) {
            if (!hasIpConfigAddress(ipConfigMap.get(String.valueOf(i)), ipv6)) {
                return i;
            }
        }
        return count + 1;
    }

    private boolean hasIpConfigAddress(String ipConfig, boolean ipv6) {
        if (ipConfig == null || ipConfig.trim().isEmpty()) {
            return false;
        }
        String key = ipv6 ? "ip6" : "ip";
        for (String item : ipConfig.split(",")) {
            String[] parts = item.trim().split("=", 2);
            if (parts.length == 2 && key.equals(parts[0].trim())) {
                return true;
            }
        }
        return false;
    }

    private void appendIpConfigItem(HashMap<String, String> ipConfigMap, int configIndex, String configValue) {
        String key = String.valueOf(configIndex);
        String oldConfig = ipConfigMap.get(key);
        if (oldConfig == null || oldConfig.trim().isEmpty()) {
            ipConfigMap.put(key, configValue);
            return;
        }
        ipConfigMap.put(key, oldConfig + "," + configValue);
    }

    private String ensureClassicIpv6Config(String ipConfig, Integer nodeId, VmParams vmParams, Master node, Set<String> selectedIpv6Set) {
        if (isVpcNetwork(vmParams) || ipConfig == null || ipConfig.contains("ip6=")) {
            return ipConfig;
        }
        Ippool ipv6Entity = getOneFreeIpForCreateVm(nodeId, vmParams.getIfnat(), node == null ? null : node.getNatippool(), selectedIpv6Set, IP_VERSION_6);
        if (ipv6Entity == null) {
            return ipConfig;
        }
        Ipstatus currentIpv6Pool = ipstatusService.getById(ipv6Entity.getPoolId());
        Integer prefix = getClassicIpPrefix(ipv6Entity, currentIpv6Pool);
        if (currentIpv6Pool == null || prefix == null) {
            return ipConfig;
        }
        selectedIpv6Set.add(ipv6Entity.getIp());
        return ipConfig + ",ip6=" + ipv6Entity.getIp() + "/" + prefix + ",gw6=" + ipv6Entity.getGateway();
    }

    private Integer getClassicIpPrefix(Ippool ippool, Ipstatus ipstatus) {
        if (ippool != null && Objects.equals(ippool.getIpVersion(), IP_VERSION_6)
                && ippool.getSubnetMask() != null && !ippool.getSubnetMask().trim().isEmpty()) {
            try {
                return Integer.parseInt(ippool.getSubnetMask().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return ipstatus == null ? null : ipstatus.getMask();
    }

    private List<String> getVpcPublicIpListForCreateVm(Integer nodeId, Master node, VmParams vmParams, int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> selectedPublicIpSet = new LinkedHashSet<>();
        if (vmParams.getPublicIpList() != null) {
            for (String ip : vmParams.getPublicIpList()) {
                if (ip == null || ip.trim().isEmpty()) {
                    continue;
                }
                String publicIp = ip.trim();
                if (!isVpcPublicIpAvailable(nodeId, node, vmParams.getPublicIpPoolId(), publicIp)) {
                    return null;
                }
                selectedPublicIpSet.add(publicIp);
                if (selectedPublicIpSet.size() >= count) {
                    return new ArrayList<>(selectedPublicIpSet);
                }
            }
        }
        Integer publicPoolId = vmParams.getPublicIpPoolId();
        Integer natPoolId = node == null ? null : node.getNatippool();
        while (selectedPublicIpSet.size() < count) {
            Ippool ippool = getOneFreePublicIpForVpc(nodeId, publicPoolId, natPoolId, node == null ? null : node.getHost(), selectedPublicIpSet);
            if (ippool == null) {
                return null;
            }
            selectedPublicIpSet.add(ippool.getIp());
        }
        return new ArrayList<>(selectedPublicIpSet);
    }

    private Ippool getOneFreePublicIpForVpc(Integer nodeId, Integer poolId, Integer natPoolId, String nodeHost, Set<String> excludeIpSet) {
        QueryWrapper<Ippool> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("node_id", nodeId);
        queryWrapper.eq("status", 0);
        if (nodeHost != null && !nodeHost.trim().isEmpty()) {
            queryWrapper.ne("ip", nodeHost.trim());
        }
        if (poolId != null) {
            queryWrapper.eq("pool_id", poolId);
        } else if (natPoolId != null) {
            queryWrapper.ne("pool_id", natPoolId);
        }
        if (excludeIpSet != null && !excludeIpSet.isEmpty()) {
            queryWrapper.notIn("ip", excludeIpSet);
        }
        queryWrapper.orderByAsc("id");
        queryWrapper.last("limit 1");
        return ippoolService.getOne(queryWrapper);
    }

    private boolean isVpcPublicIpAvailable(Integer nodeId, Master node, Integer poolId, String ip) {
        if (nodeId == null || ip == null || ip.trim().isEmpty()) {
            return false;
        }
        if (node != null && node.getHost() != null && ip.trim().equals(node.getHost().trim())) {
            return false;
        }
        QueryWrapper<Ippool> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("node_id", nodeId);
        queryWrapper.eq("ip", ip.trim());
        queryWrapper.eq("status", 0);
        if (poolId != null) {
            queryWrapper.eq("pool_id", poolId);
        }
        queryWrapper.last("limit 1");
        return ippoolService.getOne(queryWrapper) != null;
    }

    private Subnetpool reserveOneFreeVpcIpForCreateVm(Integer nodeId, Integer subnetId, Set<String> excludeIpSet,
                                                       List<Subnetpool> reservedSubnetpools) {
        if (nodeId == null || subnetId == null || subnetId <= 0) {
            return null;
        }
        Set<String> retryExcludeIpSet = new LinkedHashSet<>();
        while (true) {
            QueryWrapper<Subnetpool> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("node_id", nodeId);
            queryWrapper.eq("subnat_id", subnetId);
            queryWrapper.eq("status", 0);
            if (excludeIpSet != null && !excludeIpSet.isEmpty()) {
                queryWrapper.notIn("ip", excludeIpSet);
            }
            if (!retryExcludeIpSet.isEmpty()) {
                queryWrapper.notIn("ip", retryExcludeIpSet);
            }
            queryWrapper.orderByAsc("id");
            queryWrapper.last("limit 1");
            Subnetpool subnetpool = subnetpoolService.getOne(queryWrapper);
            if (subnetpool == null) {
                return null;
            }
            if (reserveVpcSubnetpoolForCreateVm(subnetpool)) {
                if (reservedSubnetpools != null) {
                    reservedSubnetpools.add(subnetpool);
                }
                return subnetpool;
            }
            retryExcludeIpSet.add(subnetpool.getIp());
        }
    }

    private boolean reserveSelectedVpcIpsForCreateVm(Integer nodeId, Integer subnetId, Set<String> selectedIpSet,
                                                     List<Subnetpool> reservedSubnetpools) {
        if (selectedIpSet == null || selectedIpSet.isEmpty()) {
            return true;
        }
        if (nodeId == null || subnetId == null || subnetId <= 0) {
            return false;
        }
        for (String ip : selectedIpSet) {
            if (ip == null || ip.trim().isEmpty()) {
                continue;
            }
            QueryWrapper<Subnetpool> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("node_id", nodeId);
            queryWrapper.eq("subnat_id", subnetId);
            queryWrapper.eq("ip", ip.trim());
            queryWrapper.eq("status", 0);
            queryWrapper.last("limit 1");
            Subnetpool subnetpool = subnetpoolService.getOne(queryWrapper);
            if (subnetpool == null || !reserveVpcSubnetpoolForCreateVm(subnetpool)) {
                releaseReservedVpcIpsForCreateVm(reservedSubnetpools);
                return false;
            }
            if (reservedSubnetpools != null) {
                reservedSubnetpools.add(subnetpool);
            }
        }
        return true;
    }

    private boolean reserveVpcSubnetpoolForCreateVm(Subnetpool subnetpool) {
        if (subnetpool == null || subnetpool.getId() == null) {
            return false;
        }
        UpdateWrapper<Subnetpool> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", subnetpool.getId());
        updateWrapper.eq("status", 0);
        updateWrapper.set("status", 1);
        updateWrapper.set("vm_id", 0);
        updateWrapper.set("mac", null);
        return subnetpoolService.update(updateWrapper);
    }

    private void releaseReservedVpcIpsForCreateVm(List<Subnetpool> reservedSubnetpools) {
        if (reservedSubnetpools == null || reservedSubnetpools.isEmpty()) {
            return;
        }
        for (Subnetpool subnetpool : reservedSubnetpools) {
            if (subnetpool == null || subnetpool.getId() == null) {
                continue;
            }
            UpdateWrapper<Subnetpool> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", subnetpool.getId());
            updateWrapper.eq("vm_id", 0);
            updateWrapper.eq("status", 1);
            updateWrapper.set("status", 0);
            updateWrapper.set("vm_id", 0);
            updateWrapper.set("mac", null);
            subnetpoolService.update(updateWrapper);
        }
        reservedSubnetpools.clear();
    }

    private void normalizeNetworkType(VmParams vmParams) {
        String networkType = vmParams.getNetworkType();
        if (networkType == null || networkType.trim().isEmpty()) {
            vmParams.setNetworkType(NETWORK_TYPE_CLASSIC);
            return;
        }
        networkType = networkType.trim().toLowerCase(Locale.ROOT);
        if (!NETWORK_TYPE_VPC.equals(networkType)) {
            networkType = NETWORK_TYPE_CLASSIC;
        }
        vmParams.setNetworkType(networkType);
    }

    private boolean isVpcNetwork(VmParams vmParams) {
        return vmParams != null && NETWORK_TYPE_VPC.equals(vmParams.getNetworkType());
    }

    private UnifiedResultDto<Object> invalidParam(String message) {
        return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null, message);
    }

    private String buildVpcSubnetInvalidMessage(VmParams vmParams) {
        if (vmParams == null) {
            return "VPC子网查询参数为空: vmParams不能为空";
        }
        Integer subnetId = vmParams.getVpcSubnetId();
        if (subnetId != null && subnetId > 0) {
            Subnet subnet = subnetService.getById(subnetId);
            if (subnet == null) {
                return "VPC子网不存在: vpcSubnetId=" + subnetId;
            }
            if (isNatIpPoolSubnet(subnet, vmParams.getNodeid())) {
                return "VPC子网不能选择NAT池对应的SDN子网: nodeid=" + vmParams.getNodeid()
                        + ", vpcSubnetId=" + subnetId;
            }
            if (subnet.getNodeid() == null) {
                return "VPC子网未记录节点，且子网IP池未找到当前节点记录: nodeid=" + vmParams.getNodeid()
                        + ", vpcSubnetId=" + subnetId + ", subnetNodeid=null";
            }
            if (!isSubnetBelongToNode(subnet, vmParams.getNodeid())) {
                return "VPC子网不属于当前节点: nodeid=" + vmParams.getNodeid()
                        + ", vpcSubnetId=" + subnetId + ", subnetNodeid=" + subnet.getNodeid();
            }
        }
        if (vmParams.getBridge() == null || vmParams.getBridge().trim().isEmpty()) {
            return "VPC网络需要传vpcSubnetId，或传bridge/vnet用于反查子网: nodeid=" + vmParams.getNodeid();
        }
        return "未找到匹配的VPC子网: nodeid=" + vmParams.getNodeid()
                + ", vpcSubnetId=" + vmParams.getVpcSubnetId()
                + ", bridge=" + vmParams.getBridge();
    }

    private Subnet resolveVpcSubnet(VmParams vmParams) {
        if (vmParams == null || vmParams.getNodeid() == null) {
            return null;
        }
        Integer subnetId = vmParams.getVpcSubnetId();
        if (subnetId != null && subnetId > 0) {
            Subnet subnet = subnetService.getById(subnetId);
            if (!isNatIpPoolSubnet(subnet, vmParams.getNodeid())
                    && isSubnetBelongToNode(subnet, vmParams.getNodeid())) {
                return subnet;
            }
        }
        if (vmParams.getBridge() == null || vmParams.getBridge().trim().isEmpty()) {
            return null;
        }
        List<Subnet> subnets = subnetService.lambdaQuery()
                .eq(Subnet::getVnet, vmParams.getBridge().trim())
                .list();
        if (subnets == null || subnets.isEmpty()) {
            return null;
        }
        if (subnetId != null && subnetId > 0) {
            for (Subnet subnet : subnets) {
                if (Objects.equals(subnet.getId(), subnetId)
                        && !isNatIpPoolSubnet(subnet, vmParams.getNodeid())
                        && isSubnetBelongToNode(subnet, vmParams.getNodeid())) {
                    return subnet;
                }
            }
        }
        for (Subnet subnet : subnets) {
            if (!isNatIpPoolSubnet(subnet, vmParams.getNodeid())
                    && isSubnetBelongToNode(subnet, vmParams.getNodeid())) {
                return subnet;
            }
        }
        return null;
    }

    private boolean isNatIpPoolSubnet(Subnet subnet, Integer nodeId) {
        if (subnet == null || nodeId == null) {
            return false;
        }
        Master node = masterService.getById(nodeId);
        if (node == null || node.getNatippool() == null || node.getNatippool() <= 0) {
            return false;
        }
        Ipstatus natPool = ipstatusService.getById(node.getNatippool());
        if (natPool == null || !Objects.equals(natPool.getNodeid(), nodeId)) {
            return false;
        }
        return Objects.equals(subnet.getMask(), natPool.getMask())
                && isSameAddress(getSubnetGatewayOrAddress(subnet), natPool.getGateway());
    }

    private String getSubnetGatewayOrAddress(Subnet subnet) {
        if (subnet == null) {
            return null;
        }
        if (subnet.getGateway() != null && !subnet.getGateway().trim().isEmpty()) {
            return subnet.getGateway();
        }
        return subnet.getSubnet();
    }

    private boolean isSameAddress(String left, String right) {
        String normalizedLeft = normalizeAddress(left);
        String normalizedRight = normalizeAddress(right);
        return normalizedLeft != null && normalizedLeft.equals(normalizedRight);
    }

    private String normalizeAddress(String address) {
        if (address == null) {
            return null;
        }
        String value = address.trim();
        if (value.isEmpty()) {
            return null;
        }
        int cidrIndex = value.indexOf('/');
        if (cidrIndex >= 0) {
            value = value.substring(0, cidrIndex).trim();
        }
        return value;
    }

    private boolean isSubnetBelongToNode(Subnet subnet, Integer nodeId) {
        if (subnet == null || subnet.getId() == null || nodeId == null) {
            return false;
        }
        if (subnet.getNodeid() != null) {
            return Objects.equals(subnet.getNodeid(), nodeId);
        }
        return hasSubnetpoolForNode(subnet.getId(), nodeId);
    }

    private boolean hasSubnetpoolForNode(Integer subnetId, Integer nodeId) {
        if (subnetId == null || nodeId == null) {
            return false;
        }
        QueryWrapper<Subnetpool> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("subnat_id", subnetId);
        queryWrapper.eq("node_id", nodeId);
        queryWrapper.last("limit 1");
        return subnetpoolService.getOne(queryWrapper) != null;
    }

    private String buildNet0Config(Master node, VmParams vmParams, String bandWidth, String macAddress) {
        String bridge = vmParams.getBridge();
        if (bridge == null) {
            if (vmParams.getIfnat() == 1 && node.getNatbridge() != null) {
                bridge = node.getNatbridge();
            } else {
                bridge = "vmbr0";
            }
        }
        return CloudInitNetworkUtil.buildPveNet0Config(bridge, macAddress, bandWidth);
    }

    private List<String> getNameservers(VmParams vmParams) {
        if (vmParams.getDns1() == null || vmParams.getDns1().trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> nameservers = new ArrayList<>();
        String[] items = vmParams.getDns1().split("[,\\s]+");
        for (String item : items) {
            if (item != null && !item.trim().isEmpty()) {
                nameservers.add(item.trim());
            }
        }
        return CloudInitNetworkUtil.distinctNameservers(nameservers);
    }

    private void syncCreateVmFirewallProtection(Master node, HashMap<String, String> cookieMap, VmParams vmParams,
                                                Integer vmId, ProxmoxApiUtil proxmoxApiUtil) {
        if (node == null || cookieMap == null || vmParams == null || vmId == null) {
            return;
        }
        try {
            proxmoxApiUtil.ensureFirewallEnabledAccept(node, cookieMap);
            JSONObject pveVmConfig = proxmoxApiUtil.getVmConfig(node, cookieMap, vmId);
            String net0Config = pveVmConfig == null ? null : pveVmConfig.getString("net0");
            String macAddress = CloudInitNetworkUtil.extractMacAddress(net0Config);
            String bridge = vmParams.getBridge();
            if (bridge == null) {
                bridge = vmParams.getIfnat() != null && vmParams.getIfnat() == 1 && node.getNatbridge() != null
                        ? node.getNatbridge() : "vmbr0";
            }
            String rate = formatBandwidth(vmParams.getBandwidth());
            String desiredNet0Config = CloudInitNetworkUtil.ensurePveNet0Config(net0Config, bridge, macAddress, rate, true);
            if (desiredNet0Config != null && !desiredNet0Config.equals(net0Config)) {
                proxmoxApiUtil.resetVmConfig(node, cookieMap, vmId, "net0", desiredNet0Config);
            }
            proxmoxApiUtil.ensureVmFirewallDefaultAccept(node, cookieMap, vmId);
            syncCreateVmFirewallIpSet(node, cookieMap, vmId, allowedFirewallIps(vmParams));
            proxmoxApiUtil.enableVmFirewallAntiSpoof(node, cookieMap, vmId);
            updateCreatedIppoolMac(vmParams, vmId, macAddress);
        } catch (Exception e) {
            UnifiedLogger.warn(UnifiedLogger.LogType.TASK_CREATE_VM, "初始化虚拟机防IP伪造配置失败: vmid=" + vmId + ", err=" + e.getMessage());
        }
    }

    private void syncCreateVmFirewallIpSet(Master node, HashMap<String, String> cookieMap, Integer vmId, List<String> allowedIps) {
        if (allowedIps == null || allowedIps.isEmpty()) {
            UnifiedLogger.warn(UnifiedLogger.LogType.TASK_CREATE_VM, "虚拟机防IP伪造IPSet未写入，允许IP列表为空: vmid=" + vmId);
            return;
        }
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        proxmoxApiUtil.createVmFirewallIpset(node, cookieMap, vmId, "ipfilter-net0");
        for (String ip : allowedIps) {
            if (ip == null || ip.trim().isEmpty()) {
                continue;
            }
            String cidr = CloudInitNetworkUtil.normalizeFirewallCidr(ip.trim());
            try {
                proxmoxApiUtil.addVmFirewallIpsetEntry(node, cookieMap, vmId, "ipfilter-net0", cidr);
            } catch (Exception e) {
                UnifiedLogger.error(UnifiedLogger.LogType.TASK_CREATE_VM,
                        "虚拟机防IP伪造IPSet条目写入失败: vmid={}, cidr={}, err={}", vmId, cidr, e.getMessage());
                throw e;
            }
        }
    }

    private List<String> allowedFirewallIps(VmParams vmParams) {
        List<String> ipList = new ArrayList<>();
        if (vmParams == null) {
            return new ArrayList<>();
        }
        if (vmParams.getIpList() != null) {
            for (String ip : vmParams.getIpList()) {
                if (ip != null && !ip.trim().isEmpty()) {
                    ipList.add(ip.trim());
                }
            }
        }
        if (vmParams.getIpConfig() != null) {
            ipList.addAll(CloudInitNetworkUtil.getFirewallCidrList(vmParams.getIpConfig()));
        }
        return CloudInitNetworkUtil.normalizeFirewallCidrs(ipList);
    }

    private void updateCreatedIppoolMac(VmParams vmParams, Integer vmId, String macAddress) {
        if (vmParams == null || vmParams.getNodeid() == null || vmId == null || macAddress == null || macAddress.isBlank()) {
            return;
        }
        if (isVpcNetwork(vmParams)) {
            UpdateWrapper<Subnetpool> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("node_id", vmParams.getNodeid());
            updateWrapper.eq("vm_id", vmId);
            updateWrapper.eq("status", 1);
            updateWrapper.set("mac", macAddress.toLowerCase());
            subnetpoolService.update(updateWrapper);
            return;
        }
        UpdateWrapper<Ippool> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("node_id", vmParams.getNodeid());
        updateWrapper.eq("vm_id", vmId);
        updateWrapper.eq("status", 1);
        updateWrapper.set("mac", macAddress.toLowerCase());
        ippoolService.update(updateWrapper);
    }

    private String formatBandwidth(Integer bandwidth) {
        if (bandwidth == null) {
            return null;
        }
        double bandWidthValue = bandwidth / 8.0;
        return String.format(Locale.US, "%.2f", bandWidthValue);
    }
}
