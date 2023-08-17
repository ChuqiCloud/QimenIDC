package com.chuqiyun.proxmoxveams.service.impl;

import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
import com.chuqiyun.proxmoxveams.common.UnifiedResultCode;
import com.chuqiyun.proxmoxveams.dto.UnifiedResultDto;
import com.chuqiyun.proxmoxveams.dto.VmParams;
import com.chuqiyun.proxmoxveams.entity.*;
import com.chuqiyun.proxmoxveams.service.*;
import com.chuqiyun.proxmoxveams.utils.EntityHashMapConverterUtil;
import com.chuqiyun.proxmoxveams.utils.ModUtil;
import com.chuqiyun.proxmoxveams.utils.VmUtil;
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
    private VmhostService vmhostService;
    @Resource
    private ConfigService configService;
    /**
     * 创建PVE虚拟机
     *
     */
    @Override
    public UnifiedResultDto<Object> createPveVmToParams(VmParams vmParams){
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
        // 获取可用ip最多的ip池
        Ipstatus ipPool = ipstatusService.getIpStatusMaxByNodeId(nodeId);
        if (vmParams.getIpConfig() == null || vmParams.getIpConfig().size() < 1){
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
        if (taskService.insertTask(task)){
            UnifiedLogger.log(UnifiedLogger.LogType.TASK_CREATE_VM, "创建虚拟机任务: NodeID="+nodeId+",OsType="+vmParams.getOsType()+
                    ",Sockets="+vmParams.getSockets()+",Cores="+vmParams.getCores()+",Memory="+vmParams.getMemory());
            return new UnifiedResultDto<>(UnifiedResultCode.SUCCESS, vmParams);
        }
        return new UnifiedResultDto<>(UnifiedResultCode.ERROR_CREATE_VM_FAILED, null);
    }
}
