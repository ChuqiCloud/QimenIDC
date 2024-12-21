package com.chuqiyun.proxmoxveams.dto.pvesdn;

import lombok.Data;

/**
 * @author mryunqi
 * @date 2024/1/20
 */
@Data
public class ZonesParams {
    /**
     * 	nodeId 节点id
     */
    private Integer nodeId;
    /**
     * 	插件类型 evpn | faucet | qinq | simple | vlan | vxlan
     */
    private String type;
    /**
     * sdn区域标识符
     */
    private String zone;
    /**
     * 	广播域标识符
     */
    private Boolean advertiseSubnets;
    /**
     * 	bridge 桥
     */
    private String bridge;
    /**
     * 	bridge-disable-mac-learning 禁用mac学习
     */
    private Boolean bridgeDisableMacLearning;
    /**
     * 	controller frr路由器名称
     */
    private String controller;
    /**
     * 	disable-arp-nd-suppression 禁用arp nd抑制
     */
    private Boolean disableArpNdSuppression;
    /**
     * 	dns dns api服务器
     */
    private String dns;
    /**
     * 	dnszone dns区域
     */
    private String dnszone;
    /**
     * 	dp-id 数据平面标识符
     */
    private Integer dpId;
    /**
     * 	exitnodes 集群节点列表
     */
    private String exitNodes;
    /**
     * 	exitnodes-local-routing 允许退出节点连接到evpn本地路由
     */
    private Boolean exitNodesLocalRouting;
    /**
     * 	exitnodes-primary 首先强制禁止流量到达此出口节点
     */
    private Boolean exitNodesPrimary;
    /**
     * 	ipam 使用特定的ipam
     */
    private String ipam;
    /**
     * 	mac 任播逻辑路由器 MAC 地址
     */
    private String mac;
    /**
     * 	mtu 最大传输单元
     */
    private Integer mtu;
    /**
     * 	nodes 集群节点名称列表
     */
    private String nodes;
    /**
     * 	peers 对等地址列表
     */
    private String peers;
    /**
     * reversedns 反向 DNS api 服务器
     */
    private String reverseDns;
    /**
     * 	rt-import 路线目标导入
     */
    private String rtImport;
    /**
     * 	tag 任播逻辑路由器 VLAN 标签
     */
    private Integer tag;
    /**
     * 	vlan-protocol 任播逻辑路由器 VLAN 协议 802.1q | 802.1ad
     */
    private String vlanProtocol;
    /**
     * vrf-vxlan l3vni.
     */
    private Integer vrfVxlan;

}
