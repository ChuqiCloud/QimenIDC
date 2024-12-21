package com.chuqiyun.proxmoxveams.utils;

/**
 * @author mryunqi
 * @date 2024/1/20
 */
public class SdnUtil {
    /**
    * @Author: mryunqi
    * @Description: sdn区域类型 evpn | faucet | qinq | simple | vlan | vxlan
    * @DateTime: 2024/1/20 21:25
    * @Params: String type
    * @Return boolean 是否是有效的sdn区域类型
    */
    public static boolean isValidType(String type){
        return "evpn".equals(type) || "faucet".equals(type) || "qinq".equals(type) || "simple".equals(type) || "vlan".equals(type) || "vxlan".equals(type);
    }
}
