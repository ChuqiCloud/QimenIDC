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
     * 鍒涘缓PVE铏氭嫙鏈?
     *
     */
    @Override
    public UnifiedResultDto<Object> createPveVmToParams(VmParams vmParams, boolean isApi){

        if (vmParams.getHostname() != null && !vmParams.getHostname().equals("")) {
            Vmhost vmhost = vmhostService.getVmhostByNameOne(vmParams.getHostname());
            if (vmhost != null) {
                return new UnifiedResultDto<>(UnifiedResultCode.ERROR_HOSTNAME_IS_EXIST, null);
            }
        }
        // 鍒ゆ柇nodeId鏄惁涓虹┖
        if (vmParams.getNodeid() == null) {
            return invalidParam("nodeid涓嶈兘涓虹┖");
        }
        int nodeId = vmParams.getNodeid();
        Master node = masterService.getById(nodeId);
        if (ModUtil.isNull(node)) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NODE_NOT_EXIST, null);
        }
        // 鍒ゆ柇鑺傜偣鏄惁鍙敤
        if (node.getStatus() >= 1) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NODE_NOT_AVAILABLE, null);
        }
        // 濡傛灉configureTemplateId涓嶄负绌轰笖澶т簬0
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
            // 鍒ゆ柇nested鏄惁涓虹┖
            if (configuretemplate.getNested() == null) {
                vmParams.setNested(false);
            } else {
                vmParams.setNested(configuretemplate.getNested() == 1);
            }
            // 鍒ゆ柇Devirtualization鏄惁涓虹┖
            if (configuretemplate.getDevirtualization() == null) {
                vmParams.setDevirtualization(false);
            } else {
                vmParams.setDevirtualization(configuretemplate.getDevirtualization() == 1);
            }
            // 鍒ゆ柇Kvm鏄惁涓虹┖
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
            // 灏哅ap杞崲涓篐ashMap
            HashMap<Object, Object> map = new HashMap<>();
            if (configuretemplate.getDataDisk() != null) {
                map.putAll(configuretemplate.getDataDisk());
            }
            vmParams.setDataDisk(map);
            vmParams.setBandwidth(configuretemplate.getBandwidth());
            vmParams.setOnBoot(configuretemplate.getOnBoot());
        }
        // 鍒ゆ柇甯﹀鏄惁涓虹┖
        if (vmParams.getBandwidth() == null) {
            vmParams.setBandwidth(1);
        }
        // 鍒ゆ柇nested鏄惁涓虹┖
        if (vmParams.getNested() == null) {
            vmParams.setNested(false);
        }
        // 鍒ゆ柇sockets鏄惁涓虹┖
        if (vmParams.getSockets() == null) {
            vmParams.setSockets(1);
        }
        // 灏忎簬1鐨勬椂鍊欓粯璁や负1
        else if (vmParams.getSockets() < 1) {
            vmParams.setSockets(1);
        }
        // 鍒ゆ柇threads鏄惁涓虹┖
        if (vmParams.getThreads() == null) {
            vmParams.setThreads(1);
        }
        // 灏忎簬1鐨勬椂鍊欓粯璁や负1
        else if (vmParams.getThreads() < 1) {
            vmParams.setThreads(1);
        }
        // 鍒ゆ柇cores鏄惁涓虹┖
        if (vmParams.getCores() == null) {
            vmParams.setCores(1);
        }
        // 灏忎簬1鐨勬椂鍊欓粯璁や负1
        else if (vmParams.getCores() < 1) {
            vmParams.setCores(1);
        }
        // 鍒ゆ柇cpu鏄惁涓虹┖
        if (vmParams.getCpu() == null) {
            vmParams.setCpu("kvm64");
        }
        // 鍒ゆ柇devirtualization鏄惁涓虹┖
        if (vmParams.getDevirtualization() == null) {
            vmParams.setDevirtualization(false);
        }
        // 鍒ゆ柇kvm鏄惁涓虹┖
        if (vmParams.getKvm() == null) {
            vmParams.setKvm(true);
        }
        // 鍒ゆ柇cpu鏄惁鏀寔
        if (!VmUtil.isCpuTypeExist(vmParams.getCpu())) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_CPU_TYPE_NOT_EXIST, null);
        }
        // 濡傛灉寮€鍚簡nested锛屼絾鏄痗pu蹇呴』涓篽ost鎴杕ax
        if (vmParams.getNested() && !"host".equals(vmParams.getCpu()) && !"max".equals(vmParams.getCpu())) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_CPU_TYPE_NOT_SUPPORT_NESTED, null);
        }
        // 鍒ゆ柇cpuUnits鏄惁涓虹┖
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
        // 鍒ゆ柇bwlimit鏄惁涓虹┖
        if (vmParams.getBwlimit() == null) {
            vmParams.setBwlimit(configService.getBwlimit());
        }else {
            // mb/s杞崲涓簁b/s
            vmParams.setBwlimit(vmParams.getBwlimit()*1024);
        }
        // 鍒ゆ柇arch鏄惁涓虹┖
        if (vmParams.getArch() == null) {
            vmParams.setArch("x86_64");
        }
        // 鍒ゆ柇arch鏄惁鏀寔
        else if (!VmUtil.isArchExist(vmParams.getArch())) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_ARCHITECTURE_NOT_EXIST, null);
        }
        // 鍒ゆ柇acpi鏄惁涓虹┖ 0:绂佺敤 1:鍚敤
        if (vmParams.getAcpi() == null) {
            vmParams.setAcpi(1);
        }
        // 鍒ゆ柇acpi鏄惁涓?鎴?
        else if (vmParams.getAcpi() != 0 && vmParams.getAcpi() != 1) {
            vmParams.setAcpi(1);
        }
        // 鍒ゆ柇memory鏄惁涓虹┖
        if (vmParams.getMemory() == null) {
            vmParams.setMemory(512);
        }
        // 濡傛灉storage涓虹┖鎴栦负auto锛屽垯浣跨敤鑺傜偣鑷姩閫夋嫨鐨勫瓨鍌紱鑺傜偣鏈绠楀嚭瀛樺偍鏃跺厹搴昹ocal-lvm
        vmParams.setStorage(getCreateVmStorage(vmParams.getStorage(), node));

        // 鍒ゆ柇onBoot鏄惁涓虹┖
        if (vmParams.getOnBoot() == null) {
            vmParams.setOnBoot(0);
        }
        // 鍒ゆ柇ifNat鏄惁涓虹┖
        if (vmParams.getIfnat() == null) {
            vmParams.setIfnat(0);
        }
        normalizeNetworkType(vmParams);
        // 璁剧疆缃戠粶
        if (isVpcNetwork(vmParams)) {
            Subnet subnet = resolveVpcSubnet(vmParams);
            if (subnet == null) {
                return invalidParam(buildVpcSubnetInvalidMessage(vmParams));
            }
            vmParams.setVpcSubnetId(subnet.getId());
            vmParams.setBridge(subnet.getVnet());
            vmParams.setIfnat(0);
        } else if (vmParams.getBridge() == null) {
            if(vmParams.getIfnat() == 1 && node.getNaton() == 1) //nat缃戝彛
            {
                vmParams.setBridge(node.getNatbridge());
            } else {
                vmParams.setBridge("vmbr0");
            }
        }

        // 鍒ゆ柇os涓巘emplate銆乮so鏄惁涓虹┖锛岃嚦灏戞湁涓€涓笉涓虹┖
        if (vmParams.getOs() == null && vmParams.getTemplate() == null && vmParams.getIso() == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_IMAGE_NOT_NULL, null);
        }
        Os os = osService.isExistOs(vmParams.getOs());
        // 鍒ゆ柇闀滃儚鏄惁瀛樺湪
        if (os == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_CLOUD_IMAGE_NOT_EXIST, null);
        }
        if (!osService.isNodeOsDownloaded(os, nodeId)) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_CLOUD_IMAGE_NOT_AVAILABLE, null);
        }
        vmParams.setOsName(os.getName()); // osName缁熶竴淇濆瓨闀滃儚鍚嶇О
        vmParams.setOs(os.getFileName());
        // 鍒ゆ柇osType鏄惁涓虹┖
        if (vmParams.getOsType() == null) {
            vmParams.setOsType(os.getType());
        }
        // 涓存椂淇 debian
        if(Objects.equals(vmParams.getOsType(), "debian") || Objects.equals(vmParams.getOsType(), "ubuntu")){
            vmParams.setOsType("linux");
        }
        // 鍒ゆ柇osType鏄惁鏀寔
        if (!VmUtil.isOsTypeExist(vmParams.getOsType())) {
            vmParams.setOsType("other");
        }
        // 鍒ゆ柇username鏄惁涓虹┖
        if (vmParams.getUsername() == null) {
            if (os.getOsType().equals("windows")){
                vmParams.setUsername("administrator");
            }
            else{
                vmParams.setUsername("root");
            }
        }
        // 鍒ゆ柇绯荤粺鐩樺ぇ灏忔槸鍚︿负绌?
        if (vmParams.getSystemDiskSize() == null) {
            if (os.getType().equals("windows")){
                vmParams.setSystemDiskSize(configService.getWinSystemDiskSize());
            }
            else{
                vmParams.setSystemDiskSize(configService.getLinuxSystemDiskSize());
            }
        }
        // 鍒ゆ柇password鏄惁涓虹┖
        if (vmParams.getPassword() == null) {
            // 鐢熸垚闅忔満瀵嗙爜
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
            return invalidParam("ipv4num/ipv6num不能小于0");
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
                    "VPC瀛愮綉涓瓨鍦ㄩ噸澶嶇缃慖P: nodeid=" + nodeId + ", vpcSubnetId=" + vmParams.getVpcSubnetId());
        }
        if (isVpcNetwork(vmParams) && !reserveSelectedVpcIpsForCreateVm(nodeId, vmParams.getVpcSubnetId(), selectedIpSet, reservedVpcSubnetpools)) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NO_AVAILABLE_IPV4, null,
                    "VPC瀛愮綉涓寚瀹氱殑绉佺綉IP涓嶅彲鐢? nodeid=" + nodeId + ", vpcSubnetId=" + vmParams.getVpcSubnetId());
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
                            "VPC瀛愮綉娌℃湁鍙敤绉佺綉IP: nodeid=" + nodeId + ", vpcSubnetId=" + vmParams.getVpcSubnetId());
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
                        "鍏綉IP姹犳病鏈夎冻澶熷彲鐢↖P: nodeid=" + nodeId + ", publicIpPoolId=" + vmParams.getPublicIpPoolId());
            }
            vmParams.setPublicIpList(publicIpList);
        }
        // 璁剧疆dns
        if (vmParams.getDns1() == null && ipPool != null) {
            vmParams.setDns1(ipPool.getDns1());
        }
        // 鍒ゆ柇natnum鏄惁涓虹┖
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
        // 灏唙mParams杞崲涓篐ashMap
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
            // 濡傛灉isApi涓簍rue锛屽垯寰幆绛夊緟浠诲姟瀹屾垚
            if (isApi) {
                int count = 0;
                while (true) {
                    // 濡傛灉瓒呰繃30绉掕繕娌℃湁瀹屾垚锛屽垯杩斿洖澶辫触
                    if (count >= 300) {
                        return new UnifiedResultDto<>(UnifiedResultCode.ERROR_CREATE_VM_FAILED, null);
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Task task1 = taskService.getById(task.getId());
                    // 绛変簬0琛ㄧず浠诲姟杩樻湭寮€濮?
                    if (task1.getStatus() == 0) {
                        count++;
                        continue;
                    }
                    // 绛変簬1琛ㄧず浠诲姟姝ｅ湪杩涜
                    else if (task1.getStatus() == 1) {
                        count++;
                        continue;
                    }
                    // 绛変簬4琛ㄧず浠诲姟鎴愬姛
                    else if (task1.getStatus() == 4) {
                        // 璁剧疆铏氭嫙鏈篒D
                        vmParams.setVmid(task1.getVmid());
                        // 璁剧疆铏氭嫙鏈篽ostId
                        vmParams.setHostid(task1.getHostid());
                        vmParams.setIpData(VmUtil.splitIpAddress(vmParams.getIpConfig()));
                        return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, vmParams);
                    }
                    // 绛変簬2琛ㄧず浠诲姟瀹屾垚
                    else if (task1.getStatus() == 2) {
                        // 璁剧疆铏氭嫙鏈篒D
                        vmParams.setVmid(task1.getVmid());
                        // 璁剧疆铏氭嫙鏈篽ostId
                        vmParams.setHostid(task1.getHostid());
                        /*HashMap<Object, Object> vmParamsMapResult;
                        try {
                            vmParamsMapResult = EntityHashMapConverterUtil.convertToHashMap(vmParams);
                        } catch (IllegalAccessException e) {
                            UnifiedLogger.log(UnifiedLogger.LogType.TASK_CREATE_VM, "杩斿洖鍙傛暟杞崲澶辫触");
                            e.printStackTrace();
                            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_CREATE_VM_FAILED, null);
                        }*/

                        /*vmParamsMapResult.put("ipData", VmUtil.splitIpAddress(vmParams.getIpConfig()));*/
                        vmParams.setIpData(VmUtil.splitIpAddress(vmParams.getIpConfig()));
                        return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, vmParams);
                    }
                    // 绛変簬3琛ㄧず浠诲姟澶辫触
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
     * @Description: 鍒涘缓铏氭嫙鏈?
     * @DateTime: 2023/6/21 23:41
     */
    @Override
    public Integer createPveVm(VmParams vmParams, Integer vmid) {
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        Master node = masterService.getById(vmParams.getNodeid());
        if (node == null) {
            UnifiedLogger.error(UnifiedLogger.LogType.TASK_CREATE_VM, "鍒涘缓鍩虹铏氭嫙鏈哄け璐ワ紝鑺傜偣涓嶅瓨鍦? NodeID={}", vmParams.getNodeid());
            return 0;
        }
        vmParams.setStorage(getCreateVmStorage(vmParams.getStorage(), node));
        // 鍒涘缓铏氭嫙鏈哄彲閫夊弬鏁?
        HashMap<String, Object> param = new HashMap<>();
        int vmId = vmid;
        param.put("vmid", vmId);
        param.put("name", vmParams.getHostname());
        // 璁剧疆CPU
        param.put("cpu", vmParams.getCpu());
        // 璁剧疆CPU鎻掓Ы
        param.put("sockets", vmParams.getSockets());
        // 璁剧疆CPU
        param.put("cores", vmParams.getCores());

        // 濡傛灉modelGroup涓虹┖
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
        // 璁剧疆kvm
        param.put("kvm", vmParams.getKvm());

        String primaryIpConfig = CloudInitNetworkUtil.getPrimaryIpConfig(vmParams.getIpConfig());
        if (primaryIpConfig != null) {
            param.put("ipconfig0", primaryIpConfig);
        }
        //param.put("ipconfig0", "ip=23.94.247.39/28,gw=23.94.247.33");
        // 璁剧疆DNS
        param.put("nameserver", vmParams.getDns1());
        // 璁剧疆铏氭嫙鏈簅sType
        param.put("ostype", OsTypeUtil.getOsType(vmParams.getOs(),vmParams.getOsType()));
        // 寮€鏈哄惎鍔?
        param.put("onboot", vmParams.getOnBoot());
        // 璁剧疆bwlimit
        param.put("bwlimit", vmParams.getBwlimit());
        // 璁剧疆鍐呭瓨
        param.put("memory", vmParams.getMemory());
        // 璁剧疆arch
        param.put("arch", vmParams.getArch());
        // 璁剧疆acpi
        param.put("acpi", vmParams.getAcpi());
        // 寮€鍚疩EMU Agent
        param.put("agent", 1);
        // 璁剧疆铏氭嫙鏈篶itype
        if ("windows".equals(vmParams.getOsType())){
            param.put("citype","configdrive2");
        }
        if ("linux".equals(vmParams.getOsType())){
            param.put("citype","nocloud");
        }
        // 璁剧疆铏氭嫙鏈鸿处鍙?
        param.put("ciuser",vmParams.getUsername());
        // 璁剧疆铏氭嫙鏈哄瘑鐮?
        param.put("cipassword",vmParams.getPassword());
        // 璁剧疆cloud-init
        param.put("ide2", vmParams.getStorage() + ":cloudinit");
        // 璁剧疆缃戠粶
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
                UnifiedLogger.error(UnifiedLogger.LogType.TASK_CREATE_VM, "鍐欏叆鍗曠綉鍗″IP cloud-init 閰嶇疆澶辫触: vmid=" + vmId);
                e.printStackTrace();
                return 0;
            }
            param.put("cicustom", "network=" + CloudInitNetworkUtil.getNetworkSnippetVolume(vmId));
        }

        // 鑾峰彇cookie
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
     * @Author: 鏄熺
     * @Description: 鑾峰彇鍒涘缓铏氭嫙鏈烘椂鍙敤鐨勫瓨鍌ㄥ悕绉?
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
        String configValue = (ipv6 ? "ip6=" : "ip=") + ipEntity.getIp() + "/" + currentIpPool.getMask()
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
        if (currentIpv6Pool == null || currentIpv6Pool.getMask() == null) {
            return ipConfig;
        }
        selectedIpv6Set.add(ipv6Entity.getIp());
        return ipConfig + ",ip6=" + ipv6Entity.getIp() + "/" + currentIpv6Pool.getMask() + ",gw6=" + ipv6Entity.getGateway();
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
            return "VPC瀛愮綉鍙傛暟鏃犳晥: vmParams涓虹┖";
        }
        Integer subnetId = vmParams.getVpcSubnetId();
        if (subnetId != null && subnetId > 0) {
            Subnet subnet = subnetService.getById(subnetId);
            if (subnet == null) {
                return "VPC瀛愮綉涓嶅瓨鍦? vpcSubnetId=" + subnetId;
            }
            if (isNatIpPoolSubnet(subnet, vmParams.getNodeid())) {
                return "VPC子网不能选择NAT池对应的SDN子网: nodeid=" + vmParams.getNodeid()
                        + ", vpcSubnetId=" + subnetId;
            }
            if (subnet.getNodeid() == null) {
                return "VPC瀛愮綉鏈褰曡妭鐐癸紝涓斿瓙缃慖P姹犳湭鎵惧埌褰撳墠鑺傜偣璁板綍: nodeid=" + vmParams.getNodeid()
                        + ", vpcSubnetId=" + subnetId + ", subnetNodeid=null";
            }
            if (!isSubnetBelongToNode(subnet, vmParams.getNodeid())) {
                return "VPC瀛愮綉涓嶅睘浜庡綋鍓嶈妭鐐? nodeid=" + vmParams.getNodeid()
                        + ", vpcSubnetId=" + subnetId + ", subnetNodeid=" + subnet.getNodeid();
            }
        }
        if (vmParams.getBridge() == null || vmParams.getBridge().trim().isEmpty()) {
            return "VPC缃戠粶闇€瑕佷紶vpcSubnetId锛屾垨浼燽ridge/vnet鐢ㄤ簬鍙嶆煡瀛愮綉: nodeid=" + vmParams.getNodeid();
        }
        return "鏈壘鍒板尮閰嶇殑VPC瀛愮綉: nodeid=" + vmParams.getNodeid()
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
            syncCreateVmFirewallIpSet(node, cookieMap, vmId, allowedFirewallIps(vmParams));
            proxmoxApiUtil.enableVmFirewallAntiSpoof(node, cookieMap, vmId);
            updateCreatedIppoolMac(vmParams, vmId, macAddress);
        } catch (Exception e) {
            UnifiedLogger.warn(UnifiedLogger.LogType.TASK_CREATE_VM, "鍒濆鍖栬櫄鎷熸満闃睮P浼€犻厤缃け璐? vmid=" + vmId + ", err=" + e.getMessage());
        }
    }

    private void syncCreateVmFirewallIpSet(Master node, HashMap<String, String> cookieMap, Integer vmId, List<String> allowedIps) {
        if (allowedIps == null || allowedIps.isEmpty()) {
            return;
        }
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        proxmoxApiUtil.createVmFirewallIpset(node, cookieMap, vmId, "ipfilter-net0");
        for (String ip : allowedIps) {
            String cidr = ip.contains(":") ? ip + "/128" : ip + "/32";
            proxmoxApiUtil.addVmFirewallIpsetEntry(node, cookieMap, vmId, "ipfilter-net0", cidr);
        }
    }

    private List<String> allowedFirewallIps(VmParams vmParams) {
        LinkedHashSet<String> ipSet = new LinkedHashSet<>();
        if (vmParams == null) {
            return new ArrayList<>();
        }
        if (vmParams.getIpConfig() != null) {
            ipSet.addAll(CloudInitNetworkUtil.getIpList(vmParams.getIpConfig()));
        }
        if (vmParams.getIpList() != null) {
            for (String ip : vmParams.getIpList()) {
                if (ip != null && !ip.trim().isEmpty()) {
                    ipSet.add(ip.trim());
                }
            }
        }
        return new ArrayList<>(ipSet);
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
