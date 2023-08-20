package com.chuqiyun.proxmoxveams.utils;

import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.dto.VmParams;
import com.chuqiyun.proxmoxveams.entity.Cpuinfo;
import com.chuqiyun.proxmoxveams.entity.Modelgroup;
import com.chuqiyun.proxmoxveams.entity.Smbios;
import com.chuqiyun.proxmoxveams.service.CpuinfoService;
import com.chuqiyun.proxmoxveams.service.SmbiosService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @author mryunqi
 * @date 2023/7/19
 */
public class VmUtil {
    /**
    * @Author: mryunqi
    * @Description: 根据pve虚拟机运行状态字符串返回指定状态数字
    * @DateTime: 2023/7/19 23:28
    * @Params: String initStatus pve虚拟机运行状态字符串
    * @Return Integer 指定状态数字
    */
    public static Integer getVmStatusNumByStr(String initStatus){
        // 状态有0=运行中、1=已关机、2=挂起、3=恢复中、4=暂停、5=到期、6=未知
        if ("running".equals(initStatus)){
            return 0;
        }
        if ("stopped".equals(initStatus)){
            return 1;
        }
        if ("suspended".equals(initStatus)){
            return 2;
        }
        if ("rebooting".equals(initStatus)){
            return 3;
        }
        if ("paused".equals(initStatus)){
            return 4;
        }
        return 6;
    }

    /**
    * @Author: mryunqi
    * @Description: 获取cpu类型
    * @DateTime: 2023/8/7 22:32
    * @Params:
    * @Return
    */
    public static Map<String,String> getCpuTypeMap(){
        Map<String, String> map = new HashMap<>();
        map.put("athlon","AMD");
        map.put("EPYC","AMD");
        map.put("EPYC-IBPB","AMD");
        map.put("EPYC-Milan","AMD");
        map.put("EPYC-Rome","AMD");
        map.put("Opteron_G1","AMD");
        map.put("Opteron_G2","AMD");
        map.put("Opteron_G3","AMD");
        map.put("Opteron_G4","AMD");
        map.put("Opteron_G5","AMD");
        map.put("phenom","AMD");
        map.put("486","Intel");
        map.put("Broadwell","Intel");
        map.put("Broadwell-IBRS","Intel");
        map.put("Broadwell-noTSX","Intel");
        map.put("Broadwell-noTSX-IBRS","Intel");
        map.put("Cascadelake-Server","Intel");
        map.put("Cascadelake-Server-noTSX","Intel");
        map.put("Conroe","Intel");
        map.put("core2duo","Intel");
        map.put("coreduo","Intel");
        map.put("Haswell","Intel");
        map.put("Haswell-IBRS","Intel");
        map.put("Haswell-noTSX","Intel");
        map.put("Haswell-noTSX-IBRS","Intel");
        map.put("Icelake-Client","Intel");
        map.put("Icelake-Client-noTSX","Intel");
        map.put("Icelake-Server","Intel");
        map.put("Icelake-Server-noTSX","Intel");
        map.put("IvyBridge","Intel");
        map.put("IvyBridge-IBRS","Intel");
        map.put("KnightsMill","Intel");
        map.put("Nehalem","Intel");
        map.put("Nehalem-IBRS","Intel");
        map.put("pentium","Intel");
        map.put("pentium2","Intel");
        map.put("pentium3","Intel");
        map.put("SandyBridge","Intel");
        map.put("SandyBridge-IBRS","Intel");
        map.put("Skylake-Client","Intel");
        map.put("Skylake-Client-IBRS","Intel");
        map.put("Skylake-Client-noTSX-IBRS","Intel");
        map.put("Skylake-Server","Intel");
        map.put("Skylake-Server-IBRS","Intel");
        map.put("Skylake-Server-noTSX-IBRS","Intel");
        map.put("Westmere","Intel");
        map.put("Westmere-IBRS","Intel");
        map.put("kvm32","Common KVM processor");
        map.put("kvm64","Common KVM processor");
        map.put("qemu32","Common 32-bit QEMU CPU");
        map.put("qemu64","Common 64-bit QEMU CPU");
        map.put("host","KVM processor with all supported host features");
        map.put("max","maximum supported CPU model");
        return map;
    }

    /**
     * 判断cpuType是否存在
     */
    public static boolean isCpuTypeExist(String cpuType) {
        if (cpuType == null || cpuType.isEmpty()) {
            return false;
        }
        Map<String, String> cpuTypeMap = getCpuTypeMap();
        return cpuTypeMap.containsKey(cpuType);
    }

    /**
    * @Author: mryunqi
    * @Description: 获取OS类型
    * @DateTime: 2023/8/7 23:22
    * @Return ArrayList<String> OS类型列表
    */
    public static ArrayList<String> getOsTypeList() {
        ArrayList<String> osTypeList = new ArrayList<>();
        osTypeList.add("linux");
        osTypeList.add("windows");
        osTypeList.add("solaris");
        osTypeList.add("other");
        return osTypeList;
    }

    /**
    * @Author: mryunqi
    * @Description: 判断osType是否存在
    * @DateTime: 2023/8/7 23:26
    * @Params: String osType
    * @Return boolean
    */
    public static boolean isOsTypeExist(String osType) {
        if (osType == null || osType.isEmpty()) {
            return false;
        }
        ArrayList<String> osTypeList = getOsTypeList();
        return osTypeList.contains(osType);
    }

    /**
    * @Author: mryunqi
    * @Description: 获取OS架构列表
    * @DateTime: 2023/8/7 23:45
    * @Return ArrayList<String> OS架构列表
    */
    public static ArrayList<String> getArchList(){
        ArrayList<String> osTypeList = new ArrayList<>();
        osTypeList.add("x86_64");
        osTypeList.add("arrch64");
        return osTypeList;
    }

    /**
    * @Author: mryunqi
    * @Description: 判断arch是否存在
    * @DateTime: 2023/8/7 23:47
    * @Params: String arch
    * @Return boolean
    */
    public static boolean isArchExist(String arch) {
        if (arch == null || arch.isEmpty()) {
            return false;
        }
        ArrayList<String> archList = getArchList();
        return archList.contains(arch);
    }

    /**
    * @Author: mryunqi
    * @Description: 不同场景下的args参数
    * @DateTime: 2023/8/18 23:19
    * @Params: VmParams vmParams,HashMap<String, Object> param
    * @Return HashMap<String, Object>
    */
    public static void getArgs(VmParams vmParams,HashMap<String, Object> param){
        int vcpu = vmParams.getSockets()*vmParams.getCores()*vmParams.getThreads();
        Boolean nested = vmParams.getNested();
        Boolean devirtualization = vmParams.getDevirtualization();
        // 如果同时开启嵌套虚拟化和去虚拟机化
        if (Boolean.TRUE.equals(nested) && Boolean.TRUE.equals(devirtualization)) {
            // 判断是否为windows系统
            if ("windows".equals(vmParams.getOsType())) {
                param.put("cpu", vmParams.getCpu());
                // 判断args是否为空
                if (vmParams.getArgs() == null) {
                    param.put("args", "-smp " + vcpu + ",cores=" + vmParams.getCores() + ",threads=" + vmParams.getThreads() + ",maxcpus=" + vcpu + " -cpu " + vmParams.getCpu() + ",-hypervisor,+kvm_pv_unhalt,+kvm_pv_eoi,hv_spinlocks=0x1fff,hv_vapic,hv_time,hv_reset,hv_vpindex,hv_runtime,hv_relaxed,kvm=off,hv_vendor_id=intel,hv_synic,hv_stimer,hv_spinlocks=0x1fff,hv_vapic,hv_time,hv_reset,hv_vpindex,hv_runtime,hv_relaxed,kvm=off,hv_vendor_id=intel,hv_synic,hv_stimer");
                }
                else {
                    // 清理掉args中的冗余参数
                    String cleanedArgs = vmParams.getArgs()
                            .replaceAll("-hypervisor", "")
                            .replaceAll("\\+kvm_pv_unhalt", "")
                            .replaceAll("\\+kvm_pv_eoi", "")
                            .replaceAll("hv_spinlocks=0x1fff", "")
                            .replaceAll("hv_vapic", "")
                            .replaceAll("hv_time", "")
                            .replaceAll("hv_reset", "")
                            .replaceAll("hv_vpindex", "")
                            .replaceAll("hv_runtime", "")
                            .replaceAll("hv_relaxed", "")
                            .replaceAll("kvm=off", "")
                            .replaceAll("hv_vendor_id=intel", "")
                            .replaceAll("hv_synic", "")
                            .replaceAll("hv_stimer", "")
                            .replaceAll("hv_frequencies", "")
                            .replaceAll("hv_tlbflush", "")
                            .replaceAll("hv_ipi", "")
                            .replaceAll(",+", ",")
                            .replaceAll("-cpu ", "");
                    // 清理首尾的逗号
                    if (cleanedArgs.startsWith(",")) {
                        cleanedArgs = cleanedArgs.substring(1);
                    }
                    if (cleanedArgs.endsWith(",")) {
                        cleanedArgs = cleanedArgs.substring(0, cleanedArgs.length() - 1);
                    }
                    // 清理首尾的空格
                    cleanedArgs = cleanedArgs.trim();
                    param.put("args", "-smp " + vcpu + ",cores=" + vmParams.getCores() + ",threads=" + vmParams.getThreads() + ",maxcpus=" + vcpu + " -cpu " + vmParams.getCpu()+ "," + cleanedArgs + ",-hypervisor,+kvm_pv_unhalt,+kvm_pv_eoi,hv_spinlocks=0x1fff,hv_vapic,hv_time,hv_reset,hv_vpindex,hv_runtime,hv_relaxed,kvm=off,hv_vendor_id=intel,hv_synic,hv_stimer");
                }
            } else {
                param.put("cpu", vmParams.getCpu());
                // 判断args是否为空
                if (vmParams.getArgs() == null) {
                    param.put("args", "-smp " + vcpu + ",cores=" + vmParams.getCores() + ",threads=" + vmParams.getThreads() + ",maxcpus=" + vcpu + " -cpu " + vmParams.getCpu() + ",+kvm_nested,+kvm_pv_unhalt,+kvm_pv_eoi,hv_vendor_id=proxmox,hv_spinlocks=0x1fff,hv_vapic,hv_time,hv_reset,hv_vpindex,hv_runtime,hv_relaxed,hv_synic,hv_stimer,hv_frequencies,hv_tlbflush,hv_ipi");
                }
                else {
                    // 清理掉args中的冗余参数
                    String cleanedArgs = vmParams.getArgs()
                            .replaceAll("\\+kvm_nested", "")
                            .replaceAll("\\+kvm_pv_unhalt", "")
                            .replaceAll("\\+kvm_pv_eoi", "")
                            .replaceAll("hv_vendor_id=proxmox", "")
                            .replaceAll("hv_spinlocks=0x1fff", "")
                            .replaceAll("hv_vapic", "")
                            .replaceAll("hv_time", "")
                            .replaceAll("hv_reset", "")
                            .replaceAll("hv_vpindex", "")
                            .replaceAll("hv_runtime", "")
                            .replaceAll("hv_relaxed", "")
                            .replaceAll("hv_synic", "")
                            .replaceAll("hv_stimer", "")
                            .replaceAll("hv_frequencies", "")
                            .replaceAll("hv_tlbflush", "")
                            .replaceAll("hv_ipi", "")
                            .replaceAll(",+", ",")
                            .replaceAll("-cpu ", "");
                    // 清理首尾的逗号
                    if (cleanedArgs.startsWith(",")) {
                        cleanedArgs = cleanedArgs.substring(1);
                    }
                    if (cleanedArgs.endsWith(",")) {
                        cleanedArgs = cleanedArgs.substring(0, cleanedArgs.length() - 1);
                    }
                    // 清理首尾的空格
                    cleanedArgs = cleanedArgs.trim();
                    param.put("args", "-smp " + vcpu + ",cores=" + vmParams.getCores() + ",threads=" + vmParams.getThreads() + ",maxcpus=" + vcpu + " -cpu " + vmParams.getCpu()+ "," + cleanedArgs + ",+kvm_nested,+kvm_pv_unhalt,+kvm_pv_eoi,hv_vendor_id=proxmox,hv_spinlocks=0x1fff,hv_vapic,hv_time,hv_reset,hv_vpindex,hv_runtime,hv_relaxed,hv_synic,hv_stimer,hv_frequencies,hv_tlbflush,hv_ipi");
                }
            }
        }
        // 如果开启嵌套虚拟机并没有开启去虚拟化
        else if (Boolean.TRUE.equals(nested) && Boolean.FALSE.equals(devirtualization)){
            // 判断是否为windows系统
            if ("windows".equals(vmParams.getOsType())){
                param.put("cpu", vmParams.getCpu());
                // 判断args是否为空
                if (vmParams.getArgs() == null){
                    param.put("args", "-smp "+vcpu+",cores="+vmParams.getCores()+",threads="+vmParams.getThreads() + ",maxcpus=" + vcpu +" -cpu "+vmParams.getCpu());
                }
                else {
                    // 清理掉args中的冗余参数
                    String cleanedArgs = vmParams.getArgs()
                            .replaceAll("-cpu ", "");
                    // 清理首尾的逗号
                    if (cleanedArgs.startsWith(",")) {
                        cleanedArgs = cleanedArgs.substring(1);
                    }
                    if (cleanedArgs.endsWith(",")) {
                        cleanedArgs = cleanedArgs.substring(0, cleanedArgs.length() - 1);
                    }
                    // 清理首尾的空格
                    cleanedArgs = cleanedArgs.trim();
                    param.put("args", "-smp "+vcpu+",cores="+vmParams.getCores()+",threads="+vmParams.getThreads() + ",maxcpus=" + vcpu +" cpu "+vmParams.getCpu()+","+cleanedArgs);
                }
            }else {
                param.put("cpu", vmParams.getCpu());
                // 判断args是否为空
                if (vmParams.getArgs() == null){
                    param.put("args", "-smp "+vcpu+",cores="+vmParams.getCores()+",threads="+vmParams.getThreads() + ",maxcpus=" + vcpu +" -cpu "+vmParams.getCpu());
                }
                else {
                    // 清理掉args中的冗余参数
                    String cleanedArgs = vmParams.getArgs()
                            .replaceAll("-cpu ", "");
                    // 清理首尾的逗号
                    if (cleanedArgs.startsWith(",")) {
                        cleanedArgs = cleanedArgs.substring(1);
                    }
                    if (cleanedArgs.endsWith(",")) {
                        cleanedArgs = cleanedArgs.substring(0, cleanedArgs.length() - 1);
                    }
                    // 清理首尾的空格
                    cleanedArgs = cleanedArgs.trim();
                    param.put("args", "-smp "+vcpu+",cores="+vmParams.getCores()+",threads="+vmParams.getThreads() + ",maxcpus=" + vcpu +" -cpu "+vmParams.getCpu()+","+cleanedArgs);
                }
            }
        }
        else {
            param.put("cpu", vmParams.getCpu());
            // 判断args是否为空
            if (vmParams.getArgs() == null){
                param.put("args", "-smp "+vcpu+",cores="+vmParams.getCores()+",threads="+vmParams.getThreads() + ",maxcpus=" + vcpu +" -cpu "+vmParams.getCpu()+",-vmx");
            }
            else {
                // 清理掉args中的冗余参数
                String cleanedArgs = vmParams.getArgs()
                        .replaceAll("-vmx", "")
                        .replaceAll(",+", ",")
                        .replaceAll("-cpu ", "");
                // 清理首尾的逗号
                if (cleanedArgs.startsWith(",")) {
                    cleanedArgs = cleanedArgs.substring(1);
                }
                if (cleanedArgs.endsWith(",")) {
                    cleanedArgs = cleanedArgs.substring(0, cleanedArgs.length() - 1);
                }
                // 清理首尾的空格
                cleanedArgs = cleanedArgs.trim();
                param.put("args", "-smp "+vcpu+",cores="+vmParams.getCores()+",threads="+vmParams.getThreads() + ",maxcpus=" + vcpu +" -cpu "+vmParams.getCpu()+","+cleanedArgs+",-vmx");
            }
        }


    }

    /**
    * @Author: mryunqi
    * @Description: 组合拼接模型组合args参数
    * @DateTime: 2023/8/20 22:42
    */
    public static String getArgsParamsByModelGroup(CpuinfoService cpuinfoService, SmbiosService smbiosService, Modelgroup modelgroup){
        String args = null;
        // 判断cpuModel是否为空
        if (modelgroup.getCpuModel() != null){
            // 判断cpuModel是否存在
            Cpuinfo cpuinfo = cpuinfoService.getById(modelgroup.getCpuModel());
            args = cpuinfoService.cpuinfoToString(cpuinfo);
        }
        // 判断smbios是否为空
        if (modelgroup.getSmbiosModel() != null){
            // 以逗号分割字符串
            String[] smbiosIds = modelgroup.getSmbiosModel().split(",");
            StringBuilder stringBuilder = new StringBuilder();
            for (String smbiosId : smbiosIds) {
                Smbios smbios = smbiosService.getById(Long.parseLong(smbiosId));
                stringBuilder.append(smbiosService.smbiosToStringArgs(smbios));
            }
            // 去掉最后一个逗号
            if (stringBuilder.length() > 0 && stringBuilder.charAt(stringBuilder.length() - 1) == ',') {
                stringBuilder.setLength(stringBuilder.length() - 1);
            }
            args = args + stringBuilder;
        }
        return args;
    }

    /**
    * @Author: mryunqi
    * @Description: 使用组合模组时的args参数
    * @DateTime: 2023/8/20 22:59
    */
    public static void getArgsByModelGroup(VmParams vmParams,HashMap<String, Object> param) {
        int vcpu = vmParams.getSockets() * vmParams.getCores() * vmParams.getThreads();
        Boolean nested = vmParams.getNested();
        Boolean devirtualization = vmParams.getDevirtualization();
        // 如果同时开启嵌套虚拟化和去虚拟机化
        if (Boolean.TRUE.equals(nested) && Boolean.TRUE.equals(devirtualization)) {
            // 判断是否为windows系统
            if ("windows".equals(vmParams.getOsType())) {
                param.put("cpu", vmParams.getCpu());
                param.put("args",
                        "-smp " + vcpu + ",cores=" + vmParams.getCores() + ",threads=" + vmParams.getThreads() + ",maxcpus=" + vcpu +
                                " -cpu " + vmParams.getCpu() +
                                ",-hypervisor," +
                                "+kvm_pv_unhalt," +
                                "+kvm_pv_eoi," +
                                "hv_spinlocks=0x1fff," +
                                "hv_vapic," +
                                "hv_time," +
                                "hv_reset," +
                                "hv_vpindex," +
                                "hv_runtime," +
                                "hv_relaxed," +
                                "kvm=off," +
                                "hv_vendor_id=intel," +
                                "hv_synic," +
                                "hv_stimer," +
                                "hv_spinlocks=0x1fff," +
                                "hv_vapic," +
                                "hv_time," +
                                "hv_reset," +
                                "hv_vpindex," +
                                "hv_runtime," +
                                "hv_relaxed," +
                                "kvm=off," +
                                "hv_vendor_id=intel," +
                                "hv_synic," +
                                "hv_stimer," +
                                vmParams.getArgs());
            } else {
                param.put("cpu", vmParams.getCpu());
                param.put("args",
                        "-smp " + vcpu + ",cores=" + vmParams.getCores() + ",threads=" + vmParams.getThreads() + ",maxcpus=" + vcpu +
                                " -cpu " + vmParams.getCpu() +
                                ",+kvm_nested," +
                                "+kvm_pv_unhalt," +
                                "+kvm_pv_eoi," +
                                "hv_vendor_id=proxmox," +
                                "hv_spinlocks=0x1fff," +
                                "hv_vapic," +
                                "hv_time," +
                                "hv_reset," +
                                "hv_vpindex," +
                                "hv_runtime," +
                                "hv_relaxed," +
                                "hv_synic," +
                                "hv_stimer," +
                                "hv_frequencies," +
                                "hv_tlbflush," +
                                "hv_ipi," +
                                vmParams.getArgs());
            }
        }
        // 如果开启嵌套虚拟机并没有开启去虚拟化
        else if (Boolean.TRUE.equals(nested) && Boolean.FALSE.equals(devirtualization)) {
            // 判断是否为windows系统
            if ("windows".equals(vmParams.getOsType())) {
                param.put("cpu", vmParams.getCpu());
                param.put("args",
                        "-smp " + vcpu + ",cores=" + vmParams.getCores() + ",threads=" + vmParams.getThreads() + ",maxcpus=" + vcpu +
                                " -cpu " + vmParams.getCpu() + "," +
                                vmParams.getArgs());
            } else {
                param.put("cpu", vmParams.getCpu());
                param.put("args",
                        "-smp " + vcpu + ",cores=" + vmParams.getCores() + ",threads=" + vmParams.getThreads() + ",maxcpus=" + vcpu +
                                " -cpu " + vmParams.getCpu() + "," +
                                vmParams.getArgs());
            }
        } else {
            param.put("cpu", vmParams.getCpu());
            param.put("args",
                    "-smp " + vcpu + ",cores=" + vmParams.getCores() + ",threads=" + vmParams.getThreads() + ",maxcpus=" + vcpu +
                            " -cpu " + vmParams.getCpu() + ",-vmx," +
                            vmParams.getArgs());
        }
    }
}
