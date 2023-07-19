package com.chuqiyun.proxmoxveams.utils;

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
}
