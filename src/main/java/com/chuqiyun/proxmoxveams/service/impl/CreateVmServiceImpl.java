package com.chuqiyun.proxmoxveams.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.dto.VmParams;
import com.chuqiyun.proxmoxveams.entity.*;
import com.chuqiyun.proxmoxveams.service.*;
import com.chuqiyun.proxmoxveams.utils.*;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;

import static com.chuqiyun.proxmoxveams.constant.TaskType.CREATE_VM;

/**
 * @author mryunqi
 * @date 2023/8/6
 */
@Service("createVmService")
public class CreateVmServiceImpl implements CreateVmService {
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
            vmParams.setNested(configuretemplate.getNested() == 1);
            vmParams.setDevirtualization(configuretemplate.getDevirtualization() == 1);
            vmParams.setKvm(configuretemplate.getKvm() == 1);
            vmParams.setModelGroup(configuretemplate.getModelGroup());
            vmParams.setCpuModel(configuretemplate.getCpuModel());
            vmParams.setCpuUnits(configuretemplate.getCpuUnits());
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
            vmParams.setOnBoot(configuretemplate.getOnboot());
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
        else if (!VmUtil.isCpuTypeExist(vmParams.getCpu())) {
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
        // 如果storage为空或为auto，则使用节点的最大剩余磁盘
        if (vmParams.getStorage() == null || "auto".equals(vmParams.getStorage())) {
            vmParams.setStorage(node.getAutoStorage());
        }
        // 判断username是否为空
        if (vmParams.getUsername() == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_USERNAME_NOT_NULL, null);
        }
        // 判断password是否为空
        if (vmParams.getPassword() == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_PASSWORD_NOT_NULL, null);
        }
        // 判断onBoot是否为空
        if (vmParams.getOnBoot() == null) {
            vmParams.setOnBoot(0);
        }
        // 设置网络
        if (vmParams.getBridge() == null) {
            vmParams.setBridge("vmbr0");
        }
        // 获取可用ip最多的ip池
        Ipstatus ipPool = ipstatusService.getIpStatusMaxByNodeId(nodeId);
        if (vmParams.getIpConfig() == null || vmParams.getIpConfig().size() <= 1){
            HashMap<String,String> ipConfig = new HashMap<>();
            if (ipPool == null) {
                return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NO_AVAILABLE_IPV4, null);
            }
            if (ipPool.getAvailable() == 0) {
                return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NO_AVAILABLE_IPV4, null);
            }
            // 获取可用IP
            Ippool ipEntity = ippoolService.getOneOkIpByPoolId(ipPool.getId());
            if (ipEntity == null) {
                return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NO_AVAILABLE_IPV4, null);
            }
            ipConfig.put("1","ip="+ipEntity.getIp()+"/"+ipPool.getMask()+",gw="+ipEntity.getGateway());
            vmParams.setIpConfig(ipConfig);
            // 判断hostname是否为空
            if (vmParams.getHostname() == null) {
                vmParams.setHostname(ModUtil.ipReplace(ipEntity.getIp()));
            }
        }
        else {
            int count = vmParams.getIpConfig().size();
            HashMap<String, String> ipConfigMap = vmParams.getIpConfig();
            for (int i = 1; i <= count; i++) {
                String ipConfig = ipConfigMap.get(String.valueOf(i));
                if (ipConfig == null) {
                    if (ipPool == null) {
                        return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NO_AVAILABLE_IPV4, null);
                    }
                    if (ipPool.getAvailable() == 0) {
                        return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NO_AVAILABLE_IPV4, null);
                    }
                    // 获取可用IP
                    Ippool ipEntity = ippoolService.getOneOkIpByPoolId(ipPool.getId());
                    if (ipEntity == null) {
                        return new UnifiedResultDto<>(UnifiedResultCode.ERROR_NO_AVAILABLE_IPV4, null);
                    }
                    // 修改vmParams
                    ipConfigMap.put(String.valueOf(i), "ip=" + ipEntity.getIp() + "/" + ipPool.getMask() + ",gw=" + ipEntity.getGateway());
                    // 判断hostname是否为空
                    if (vmParams.getHostname() == null) {
                        vmParams.setHostname(ModUtil.ipReplace(ipEntity.getIp()));
                    }
                }
            }
            // 设置ip地址
            vmParams.setIpConfig(ipConfigMap);
        }
        // 设置dns
        if (vmParams.getDns1() == null) {
            vmParams.setDns1(ipPool.getDns1());
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
        else {
            vmParams.setOs(os.getFileName());
        }
        int osStatus = osService.getNodeOsStatus(vmParams.getOs(), nodeId);
        // 判断镜像是否可用
        if (osStatus != 2) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_CLOUD_IMAGE_NOT_AVAILABLE, null);
        }
        // 判断osType是否为空
        if (vmParams.getOsType() == null) {
            return new UnifiedResultDto<>(UnifiedResultCode.ERROR_OS_TYPE_NOT_NULL, null);
        }
        // 判断osType是否支持
        if (!VmUtil.isOsTypeExist(vmParams.getOsType())) {
            vmParams.setOsType("other");
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
                        return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, vmParams);
                    }
                    // 等于2表示任务完成
                    else if (task1.getStatus() == 2) {
                        // 设置虚拟机ID
                        vmParams.setVmid(task1.getVmid());
                        // 设置虚拟机hostId
                        vmParams.setHostid(task1.getHostid());
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
        // 创建虚拟机可选参数
        HashMap<String, Object> param = new HashMap<>();
        int vmId = vmid;
        param.put("vmid", vmId);
        param.put("name", vmParams.getHostname());
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

        int ipCount = vmParams.getIpConfig().size();
        for (int i = 0; i < ipCount; i++) {
            param.put("ipconfig"+i, vmParams.getIpConfig().get(String.valueOf(i+1)));
        }
        //param.put("ipconfig0", "ip=23.94.247.39/28,gw=23.94.247.33");
        // 设置DNS
        param.put("nameserver", vmParams.getDns1());
        // 设置虚拟机osType
        param.put("ostype", OsTypeUtil.getOsType(vmParams.getOs(),vmParams.getOsType()));
        // 开机启动
        param.put("onboot", vmParams.getOnBoot());
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
        if (vmParams.getStorage() == null){
            param.put("ide2", "local-lvm:cloudinit");
        }else if ("auto".equals(vmParams.getStorage())) {
            param.put("ide2", "local-lvm:cloudinit");
        }else {
            param.put("ide2", vmParams.getStorage()+":cloudinit");
        }
        // 设置网络
        if (vmParams.getBridge() == null) {
            param.put("net0", "virtio,bridge=vmbr0,rate="+vmParams.getBandwidth());
        }else {
            param.put("net0", "virtio,bridge="+vmParams.getBridge());
        }
        // 获取cookie
        HashMap<String, String> authentications = masterService.getMasterCookieMap(vmParams.getNodeid());
        JSONObject jsonObject =  proxmoxApiUtil.postNodeApi(node,authentications, "/nodes/"+node.getNodeName()+"/qemu", param);
        if (jsonObject.containsKey("data")){
            return vmId;
        }else {
            return 0;
        }
    }
}
