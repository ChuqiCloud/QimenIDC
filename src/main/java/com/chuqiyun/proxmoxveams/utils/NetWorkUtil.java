package com.chuqiyun.proxmoxveams.utils;

import com.chuqiyun.proxmoxveams.dto.NetWorkParams;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author mryunqi
 * @date 2024/1/17
 */
public class NetWorkUtil {
    /**
    * @Author: mryunqi
    * @Description: 节点网络类型
    * @DateTime: 2024/1/17 20:16
    * @Return List<String> 网络类型列表
    */
    public static List<String> getNetWorkTypeList(){
        List<String> netWorkTypeList = new ArrayList<>();
        netWorkTypeList.add("bridge");
        netWorkTypeList.add("bond");
        netWorkTypeList.add("eth");
        netWorkTypeList.add("alias");
        netWorkTypeList.add("vlan");
        netWorkTypeList.add("OVSBridge");
        netWorkTypeList.add("OVSBond");
        netWorkTypeList.add("OVSPort");
        netWorkTypeList.add("OVSIntPort");
        netWorkTypeList.add("unknown");
        return netWorkTypeList;
    }

    /**
    * @Author: mryunqi
    * @Description: Bonding mode列表
    * @DateTime: 2024/1/17 20:33
    * @Return List<String> Bonding mode列表
    */
    public static List<String> getBondingModeList(){
        List<String> bondingModeList = new ArrayList<>();
        bondingModeList.add("balance-rr");
        bondingModeList.add("active-backup");
        bondingModeList.add("balance-xor");
        bondingModeList.add("broadcast");
        bondingModeList.add("802.3ad");
        bondingModeList.add("balance-tlb");
        bondingModeList.add("balance-alb");
        bondingModeList.add("balance-slb");
        bondingModeList.add("lacp-balance-slb");
        bondingModeList.add("lacp-balance-tcp");
        return bondingModeList;
    }

    /**
    * @Author: mryunqi
    * @Description: bond_xmit_hash_policy 列表
    * @DateTime: 2024/1/17 20:37
    * @Return   List<String> bond_xmit_hash_policy 列表
    */
    public static List<String> getBondXmitHashPolicyList(){
        List<String> bondXmitHashPolicyList = new ArrayList<>();
        bondXmitHashPolicyList.add("layer2");
        bondXmitHashPolicyList.add("layer2+3");
        bondXmitHashPolicyList.add("layer3+4");
        return bondXmitHashPolicyList;
    }

    /**
    * @Author: mryunqi
    * @Description: 网卡参数转换
    * @DateTime: 2024/1/17 20:55
    * @Params: NetWorkParams 网卡参数
    * @Return Map<String, Object> 网卡参数
    */
    public static HashMap<String,Object> getNetWorkParamsMap(NetWorkParams netWorkParams) {
        HashMap<String, Object> netWorkParamsMap = new HashMap<>();
        if (netWorkParams.getIface() != null) {
            netWorkParamsMap.put("iface", netWorkParams.getIface());
        }
        if (netWorkParams.getNode() != null) {
            netWorkParamsMap.put("node", netWorkParams.getNode());
        }
        if (netWorkParams.getType() != null) {
            netWorkParamsMap.put("type", netWorkParams.getType());
        }
        if (netWorkParams.getAddress() != null) {
            netWorkParamsMap.put("address", netWorkParams.getAddress());
        }
        if (netWorkParams.getAddress6() != null) {
            netWorkParamsMap.put("address6", netWorkParams.getAddress6());
        }
        if (netWorkParams.getAutostart() != null) {
            netWorkParamsMap.put("autostart", netWorkParams.getAutostart());
        }
        if (netWorkParams.getBondPrimary() != null) {
            netWorkParamsMap.put("bond-primary", netWorkParams.getBondPrimary());
        }
        if (netWorkParams.getBondMode() != null) {
            netWorkParamsMap.put("bond-mode", netWorkParams.getBondMode());
        }
        if (netWorkParams.getBondXmitHashPolicy() != null) {
            netWorkParamsMap.put("bond-xmit-hash-policy", netWorkParams.getBondXmitHashPolicy());
        }
        if (netWorkParams.getBridgePorts() != null) {
            netWorkParamsMap.put("bridge-ports", netWorkParams.getBridgePorts());
        }
        if (netWorkParams.getBridgeVlanAware() != null) {
            netWorkParamsMap.put("bridge-vlan-aware", netWorkParams.getBridgeVlanAware());
        }
        if (netWorkParams.getCidr() != null) {
            netWorkParamsMap.put("cidr", netWorkParams.getCidr());
        }
        if (netWorkParams.getCidr6() != null) {
            netWorkParamsMap.put("cidr6", netWorkParams.getCidr6());
        }
        if (netWorkParams.getComments() != null) {
            netWorkParamsMap.put("comments", netWorkParams.getComments());
        }
        if (netWorkParams.getComments6() != null) {
            netWorkParamsMap.put("comments6", netWorkParams.getComments6());
        }
        if (netWorkParams.getDelete() != null) {
            netWorkParamsMap.put("delete", netWorkParams.getDelete());
        }
        if (netWorkParams.getGateway() != null) {
            netWorkParamsMap.put("gateway", netWorkParams.getGateway());
        }
        if (netWorkParams.getGateway6() != null) {
            netWorkParamsMap.put("gateway6", netWorkParams.getGateway6());
        }
        if (netWorkParams.getMtu() != null) {
            netWorkParamsMap.put("mtu", netWorkParams.getMtu().toString());
        }
        if (netWorkParams.getNetmask() != null) {
            netWorkParamsMap.put("netmask", netWorkParams.getNetmask());
        }
        if (netWorkParams.getNetmask6() != null) {
            netWorkParamsMap.put("netmask6", netWorkParams.getNetmask6());
        }
        if (netWorkParams.getOvsBonds() != null) {
            netWorkParamsMap.put("ovs-bonds", netWorkParams.getOvsBonds());
        }
        if (netWorkParams.getOvsBridge() != null) {
            netWorkParamsMap.put("ovs-bridge", netWorkParams.getOvsBridge());
        }
        if (netWorkParams.getOvsOptions() != null) {
            netWorkParamsMap.put("ovs-options", netWorkParams.getOvsOptions());
        }
        if (netWorkParams.getOvsPorts() != null) {
            netWorkParamsMap.put("ovs-ports", netWorkParams.getOvsPorts());
        }
        if (netWorkParams.getOvsTag() != null) {
            netWorkParamsMap.put("ovs-tag", netWorkParams.getOvsTag());
        }
        if (netWorkParams.getSlaves() != null) {
            netWorkParamsMap.put("slaves", netWorkParams.getSlaves());
        }
        if (netWorkParams.getVlanId() != null) {
            netWorkParamsMap.put("vlan-id", netWorkParams.getVlanId().toString());
        }
        if (netWorkParams.getVlanRawDevice() != null) {
            netWorkParamsMap.put("vlan-raw-device", netWorkParams.getVlanRawDevice());
        }
        return netWorkParamsMap;
    }
}
