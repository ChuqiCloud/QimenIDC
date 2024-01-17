package com.chuqiyun.proxmoxveams.dto;

import lombok.Data;

/**
 * @author mryunqi
 * @date 2024/1/17
 */
@Data
public class NetWorkParams {
    /**
     * iface 网卡名称
     */
    private String iface;
    /**
     * node 节点名称
     */
    private String node;
    /**
     * type 网卡类型
     */
    private String type;

    /**
     * address 网卡地址
     */
    private String address;
    /**
     * address6 网卡地址6
     */
    private String address6;
    /**
     * autostart 是否自动启动
     */
    private Boolean autostart;
    /**
     * bond-primary 网卡bond主网卡
     */
    private String bondPrimary;
    /**
     * bond_mode 网卡bond模式
     */
    private String bondMode;
    /**
     * bond_xmit_hash_policy 网卡bond_xmit_hash_policy
     */
    private String bondXmitHashPolicy;
    /**
     * bridge_ports 网卡bridge_ports
     */
    private String bridgePorts;
    /**
     * bridge_vlan_aware 网卡bridge_vlan_aware
     */
    private Boolean bridgeVlanAware;
    /**
     * cidr 网卡cidr ipv4
     */
    private String cidr;
    /**
     * cidr6 网卡cidr ipv6
     */
    private String cidr6;
    /**
     * comments 网卡comments
     */
    private String comments;
    /**
     * comments6 网卡comments6
     */
    private String comments6;
    /**
     * delete  删除一个设置
     */
    private String delete;
    /**
     * gateway 默认网关
     */
    private String gateway;
    /**
     * gateway6 默认ipv6网关
     */
    private String gateway6;
    /**
     * mtu 网卡mtu 区间1280 - 65520
     */
    private Integer mtu;
    /**
     * netmask ipv4子网掩码
     */
    private String netmask;
    /**
     * netmask6 ipv6子网掩码  (0 - 128)
     */
    private Integer netmask6;
    /**
     * ovs_bonds 绑定的接口
     */
    private String ovsBonds;
    /**
     * ovs_bridge 网桥名称
     */
    private String ovsBridge;
    /**
     * ovs_options 网桥参数
     */
    private String ovsOptions;
    /**
     * ovs_ports 网桥端口
     */
    private String ovsPorts;
    /**
     * ovs_tag 网桥tag
     */
    private Integer ovsTag;
    /**
     * slaves 网卡bond从网卡
     */
    private String slaves;
    /**
     * vlan-id
     */
    private Integer vlanId;
    /**
     * vlan-raw-device
     */
    private String vlanRawDevice;
}
