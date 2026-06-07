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
    /**
     * 创建PVE虚拟机
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
        // 判断nodeId是否为空
        if (vmParams.getNodeid() == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_INVALID_PARAM, null);
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
            // 将Map转换为HashMap
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
        // 小于1的时候默认为1
        else if (vmParams.getSockets() < 1) {
            vmParams.setSockets(1);
        }
        // 判断threads是否为空
        if (vmParams.getThreads() == null) {
            vmParams.setThreads(1);
        }
        // 小于1的时候默认为1
        else if (vmParams.getThreads() < 1) {
            vmParams.setThreads(1);
        }
        // 判断cores是否为空
        if (vmParams.getCores() == null) {
            vmParams.setCores(1);
        }
        // 小于1的时候默认为1
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
        // 如果开启了nested，但是cpu必须为host或max
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
        // 如果storage为空或为auto，则使用节点自动选择的存储；节点未计算出存储时兜底local-lvm
        vmParams.setStorage(getCreateVmStorage(vmParams.getStorage(), node));

        // 判断onBoot是否为空
        if (vmParams.getOnBoot() == null) {
            vmParams.setOnBoot(0);
        }
        // 判断ifNat是否为空
        if (vmParams.getIfnat() == null) {
            vmParams.setIfnat(0);
        }
        // 设置网络
        if (vmParams.getBridge() == null) {
            if(vmParams.getIfnat() == 1 && node.getNaton() == 1) //nat网口
            {
                vmParams.setBridge(node.getNatbridge());
            } else {
                vmParams.setBridge("vmbr0");
            }
        }

        // 判断os与template、iso是否为空，至少有一个不为空
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
        // 临时修复 debian
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
        // 获取默认IP池，仅用于DNS默认值；实际分配以ippool实时空闲状态为准
        Ipstatus ipPool = getDefaultCreateVmIpPool(nodeId, node, vmParams.getIfnat());

        HashMap<String, String> ipConfigMap = vmParams.getIpConfig() == null ? new HashMap<>() : vmParams.getIpConfig();
        int ipCount = getIpConfigCount(ipConfigMap);
        Set<String> selectedIpSet = new LinkedHashSet<>(CloudInitNetworkUtil.getIpList(ipConfigMap));
        for (int i = 1; i <= ipCount; i++) {
            String ipConfig = ipConfigMap.get(String.valueOf(i));
            if (CloudInitNetworkUtil.getIpFromCloudInitConfig(ipConfig) != null) {
                continue;
            }
            Ippool ipEntity = getOneFreeIpForCreateVm(nodeId, vmParams.getIfnat(), node.getNatippool(), selectedIpSet);
            if (ipEntity == null) {
                return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NO_AVAILABLE_IPV4, null);
            }
            Ipstatus currentIpPool = ipstatusService.getById(ipEntity.getPoolId());
            if (currentIpPool == null) {
                return new UnifiedResultDto<>(UnifiedResultCode.ERROR_IP_POOL_NOT_EXIST, null);
            }
            ipConfigMap.put(String.valueOf(i), "ip=" + ipEntity.getIp() + "/" + currentIpPool.getMask() + ",gw=" + ipEntity.getGateway());
            selectedIpSet.add(ipEntity.getIp());
            if (ipPool == null) {
                ipPool = currentIpPool;
            }
        }
        vmParams.setIpConfig(ipConfigMap);
        List<String> ipList = CloudInitNetworkUtil.getIpList(ipConfigMap);
        if (ipList.isEmpty()) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NO_AVAILABLE_IPV4, null);
        }
        if (vmParams.getHostname() == null) {
            vmParams.setHostname(ModUtil.ipReplace(ipList.get(0)));
        } else if (ModUtil.isChinese(vmParams.getHostname())) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_HOSTNAME_NOT_CHINESE, null);
        }
        vmParams.setIpList(ipList);
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
        // 将vmParams转换为HashMap
        HashMap<Object, Object> vmParamsMap;
        try {
            vmParamsMap = EntityHashMapConverterUtil.convertToHashMap(vmParams);
        } catch (IllegalAccessException e) {
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
            UnifiedLogger.log(UnifiedLogger.LogType.TASK_CREATE_VM, "创建虚拟机任务: NodeID="+nodeId+",OsType="+vmParams.getOsType()+
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
            UnifiedLogger.error(UnifiedLogger.LogType.TASK_CREATE_VM, "创建基础虚拟机失败，节点不存在: NodeID={}", vmParams.getNodeid());
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
        // 设置CPU插槽
        param.put("sockets", vmParams.getSockets());
        // 设置CPU
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
        // 设置虚拟机osType
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
        // 开启QEMU Agent
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
        // 设置网络
        double bandWidthValue = vmParams.getBandwidth() / 8.0;
        String bandWidth = String.format(Locale.US, "%.2f", bandWidthValue);
        boolean multiIp = CloudInitNetworkUtil.getIpAddressCount(vmParams.getIpConfig()) > 1;
        String macAddress = multiIp ? CloudInitNetworkUtil.buildStableMacAddress(vmParams.getNodeid(), vmId) : null;
        param.put("net0", buildNet0Config(node, vmParams, bandWidth, macAddress));
        if (multiIp) {
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

    private Ipstatus getDefaultCreateVmIpPool(Integer nodeId, Master node, Integer ifnat) {
        if (Objects.equals(ifnat, 1)) {
            return ipstatusService.getIpStatusMaxByNodeId(nodeId, node.getNatippool(), null);
        }
        return ipstatusService.getIpStatusMaxByNodeId(nodeId, null, node.getNatippool());
    }

    private Ippool getOneFreeIpForCreateVm(Integer nodeId, Integer ifnat, Integer natippool, Set<String> excludeIpSet) {
        QueryWrapper<Ippool> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("node_id", nodeId);
        queryWrapper.eq("status", 0);
        if (Objects.equals(ifnat, 1) && natippool != null) {
            queryWrapper.eq("pool_id", natippool);
        } else if (!Objects.equals(ifnat, 1) && natippool != null) {
            queryWrapper.ne("pool_id", natippool);
        }
        if (excludeIpSet != null && !excludeIpSet.isEmpty()) {
            queryWrapper.notIn("ip", excludeIpSet);
        }
        queryWrapper.orderByAsc("id");
        queryWrapper.last("limit 1");
        return ippoolService.getOne(queryWrapper);
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
            UnifiedLogger.warn(UnifiedLogger.LogType.TASK_CREATE_VM, "初始化虚拟机防IP伪造配置失败: vmid=" + vmId + ", err=" + e.getMessage());
        }
    }

    private void syncCreateVmFirewallIpSet(Master node, HashMap<String, String> cookieMap, Integer vmId, List<String> allowedIps) {
        if (allowedIps == null || allowedIps.isEmpty()) {
            return;
        }
        ProxmoxApiUtil proxmoxApiUtil = new ProxmoxApiUtil();
        proxmoxApiUtil.createVmFirewallIpset(node, cookieMap, vmId, "ipfilter-net0");
        for (String ip : allowedIps) {
            proxmoxApiUtil.addVmFirewallIpsetEntry(node, cookieMap, vmId, "ipfilter-net0", ip + "/32");
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
