package com.chuqiyun.proxmoxveams.utils;

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
        map.put("486","Intel");
        map.put("486-v1","Intel");
        map.put("Broadwell","Intel");
        map.put("Broadwell-IBRS","Intel");
        map.put("Broadwell-noTSX-IBRS","Intel");
        map.put("Broadwell-v1","Intel Core Processor (Broadwell)");
        map.put("Broadwell-v2","Intel Core Processor (Broadwell, no TSX)");
        map.put("Broadwell-v3","Intel Core Processor (Broadwell, IBRS)");
        map.put("Broadwell-v4","Intel Core Processor (Broadwell, IBRS, no TSX)");
        map.put("Cascadelake-Server","Intel");
        map.put("Cascadelake-Server-noTSX","Intel");
        map.put("Cascadelake-Server-noTSX-IBRS","Intel");
        map.put("Cascadelake-Server-v1","Intel Xeon Processor (Cascadelake)");
        map.put("Cascadelake-Server-v2","Intel Xeon Processor (Cascadelake) [ARCH_CAPABILITIES]");
        map.put("Cascadelake-Server-v3","Intel Xeon Processor (Cascadelake) [ARCH_CAPABILITIES, no TSX]");
        map.put("Cascadelake-Server-v4","Intel Xeon Processor (Cascadelake) [ARCH_CAPABILITIES, no TSX]");
        map.put("Conroe","Intel");
        map.put("Conroe-v1","Intel Celeron_4x0 (Conroe/Merom Class Core 2)");
        map.put("Cooperlake","Intel");
        map.put("Cooperlake-v1","Intel Xeon Processor (Cooperlake)");
        map.put("Denverton","Intel");
        map.put("Denverton-v1","Intel Atom Processor (Denverton)");
        map.put("Denverton-v2","Intel Atom Processor (Denverton) [no MPX, no MONITOR]");
        map.put("Dhyana","AMD");
        map.put("Dhyana-v1","AMD EPYC (Dhyana)");
        map.put("EPYC","AMD");
        map.put("EPYC-IBPB","AMD");
        map.put("EPYC-Milan","AMD");
        map.put("EPYC-Milan-v1","AMD");
        map.put("EPYC-Rome","AMD");
        map.put("EPYC-Rome-v1","AMD EPYC-Rome Processor");
        map.put("EPYC-Rome-v2","AMD EPYC-Rome Processor");
        map.put("EPYC-v1","AMD EPYC");
        map.put("EPYC-v2","AMD EPYC");
        map.put("EPYC-v3","AMD EPYC");
        map.put("Haswell","Intel");
        map.put("Haswell-IBRS","Intel");
        map.put("Haswell-noTSX","Intel");
        map.put("Haswell-noTSX-IBRS","Intel");
        map.put("Haswell-v1","Intel Core Processor (Haswell)");
        map.put("Haswell-v2","Intel Core Processor (Haswell, no TSX)");
        map.put("Haswell-v3","Intel Core Processor (Haswell, IBRS)");
        map.put("Haswell-v4","Intel Core Processor (Haswell, IBRS, no TSX)");
        map.put("Icelake-Client","Intel");
        map.put("Icelake-Client-noTSX","Intel");
        map.put("Icelake-Client-v1","Intel Core Processor (Icelake) [deprecated]");
        map.put("Icelake-Client-v2","Intel Core Processor (Icelake) [no TSX, deprecated]");
        map.put("Icelake-Server","Intel");
        map.put("Icelake-Server-noTSX","Intel");
        map.put("Icelake-Server-v1","Intel Xeon Processor (Icelake)");
        map.put("Icelake-Server-v2","Intel Xeon Processor (Icelake) [no TSX]");
        map.put("Icelake-Server-v3","Intel Xeon Processor (Icelake) [no TSX, IBRS]");
        map.put("Icelake-Server-v4","Intel Xeon Processor (Icelake) [no TSX, IBRS]");
        map.put("IvyBridge","Intel");
        map.put("IvyBridge-IBRS","Intel");
        map.put("IvyBridge-v1","Intel Xeon E3-12xx v2 (Ivy Bridge)");
        map.put("IvyBridge-v2","Intel Xeon E3-12xx v2 (Ivy Bridge, IBRS)");
        map.put("KnightsMill","Intel");
        map.put("KnightsMill-v1","Intel Xeon Phi Processor (Knights Mill)");
        map.put("Nehalem","Intel");
        map.put("Nehalem-IBRS","Intel");
        map.put("Nehalem-v1","Intel Core i7 9xx (Nehalem Class Core i7)");
        map.put("Nehalem-v2","Intel Core i7 9xx (Nehalem Class Core i7, IBRS update)");
        map.put("Opteron_G1","AMD");
        map.put("Opteron_G1-v1","AMD Opteron 240 (Gen 1 Class Opteron)");
        map.put("Opteron_G2","AMD");
        map.put("Opteron_G2-v1","AMD Opteron 22xx (Gen 2 Class Opteron)");
        map.put("Opteron_G3","AMD");
        map.put("Opteron_G3-v1","AMD Opteron 23xx (Gen 3 Class Opteron)");
        map.put("Opteron_G4","AMD");
        map.put("Opteron_G4-v1","AMD Opteron 62xx class CPU");
        map.put("Opteron_G5","AMD");
        map.put("Opteron_G5-v1","AMD Opteron 63xx class CPU");
        map.put("Penryn","Intel");
        map.put("Penryn-v1","Intel Core 2 Duo P9xxx (Penryn Class Core 2)");
        map.put("SandyBridge","Intel");
        map.put("SandyBridge-IBRS","Intel");
        map.put("SandyBridge-v1","Intel Xeon E312xx (Sandy Bridge)");
        map.put("SandyBridge-v2","Intel Xeon E312xx (Sandy Bridge, IBRS update)");
        map.put("Skylake-Client","Intel");
        map.put("Skylake-Client-IBRS","Intel");
        map.put("Skylake-Client-noTSX-IBRS","Intel");
        map.put("Skylake-Client-v1","Intel Core Processor (Skylake)");
        map.put("Skylake-Client-v2","Intel Core Processor (Skylake, IBRS)");
        map.put("Skylake-Client-v3","Intel Core Processor (Skylake, no TSX, IBRS)");
        map.put("Skylake-Server","Intel");
        map.put("Skylake-Server-IBRS","Intel");
        map.put("Skylake-Server-noTSX-IBRS","Intel");
        map.put("Skylake-Server-v1","Intel Xeon Processor (Skylake)");
        map.put("Skylake-Server-v2","Intel Xeon Processor (Skylake, IBRS)");
        map.put("Skylake-Server-v3","Intel Xeon Processor (Skylake, no TSX, IBRS)");
        map.put("Skylake-Server-v4","Intel Xeon Processor (Skylake, no TSX, IBRS)");
        map.put("Snowridge","Intel");
        map.put("Snowridge-v1","Intel Atom Processor (SnowRidge)");
        map.put("Snowridge-v2","Intel Atom Processor (Snowridge, no MPX)");
        map.put("Westmere","Intel");
        map.put("Westmere-IBRS","Intel");
        map.put("Westmere-v1","Intel Xeon E56xx/L56xx/X56xx (Westmere)");
        map.put("Westmere-v2","Intel Xeon E56xx/L56xx/X56xx (Westmere, IBRS update)");
        map.put("athlon","AMD");
        map.put("athlon-v1","AMD Athlon(tm) Processor Model 4xxx");
        map.put("core2duo","Intel");
        map.put("core2duo-v1","Intel(R) Core(TM)2 Duo CPU     T7700  @ 2.40GHz");
        map.put("coreduo","Intel");
        map.put("coreduo-v1","Genuine Intel(R) CPU           T2600  @ 2.16GHz");
        map.put("kvm32","Common KVM processor");
        map.put("kvm32-v1","Common 32-bit KVM processor");
        map.put("kvm64","Common KVM processor");
        map.put("kvm64-v1","Common KVM processor");
        map.put("n270","Intel");
        map.put("n270-v1","Intel(R) Atom(TM) CPU N270   @ 1.60GHz");
        map.put("pentium","Intel");
        map.put("pentium-v1","Genuine Intel(R) CPU           4 2.40GHz");
        map.put("pentium2","Intel");
        map.put("pentium2-v1","Genuine Intel(R) CPU           0000  @ 2.40GHz");
        map.put("pentium3","Intel");
        map.put("pentium3-v1","Genuine Intel(R) CPU           0000  @ 2.40GHz");
        map.put("phenom","AMD");
        map.put("phenom-v1","AMD Phenom(tm) 9550 Quad-Core Processor");
        map.put("qemu32","Common 32-bit QEMU CPU");
        map.put("qemu32-v1","Common 32-bit QEMU CPU");
        map.put("qemu64","Common 64-bit QEMU CPU");
        map.put("qemu64-v1","Common 64-bit QEMU CPU");
        map.put("base","base CPU model type with no features enabled");
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
}
