package com.chuqiyun.proxmoxveams.utils;

import com.chuqiyun.proxmoxveams.dto.IpParams;
import com.chuqiyun.proxmoxveams.entity.Ippool;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mryunqi
 * @date 2023/7/2
 */
public class IpUtil {
    /**
    * @Author: mryunqi
    * @Description: 根据网关及掩码位计算所有ip地址
    * @DateTime: 2023/7/2 20:29
    * @Params: IpParams ipParams
    * @Return List<Ippool>
    */
    public static List<Ippool> getIpList(IpParams ipParams){
        String gateway = ipParams.getGateway();
        Integer mask = ipParams.getMask();
        Integer nodeId = ipParams.getNodeId();
        String dns1 = ipParams.getDns1();
        String dns2 = ipParams.getDns2();
        List<Ippool> ipList = new ArrayList<>();
        // 将网关地址转换为整数形式
        String[] gatewayParts = gateway.split("\\.");
        int gatewayIP = (Integer.parseInt(gatewayParts[0]) << 24) |
                (Integer.parseInt(gatewayParts[1]) << 16) |
                (Integer.parseInt(gatewayParts[2]) << 8) |
                Integer.parseInt(gatewayParts[3]);
        // 计算子网掩码
        int subnetMask = 0xFFFFFFFF << (32 - mask);

        // 计算网络地址
        int networkAddress = gatewayIP & subnetMask;

        // 计算可用IP地址
        int hostMin = networkAddress + 1;
        int hostMax = networkAddress + (1 << (32 - mask)) - 2;

        // 将子网掩码转换为字符串形式
        String subnetMaskString = ((subnetMask >> 24) & 0xFF) + "." +
                ((subnetMask >> 16) & 0xFF) + "." +
                ((subnetMask >> 8) & 0xFF) + "." +
                (subnetMask & 0xFF);

        // 将整数形式的IP地址转换为字符串形式
        for (int i = hostMin; i <= hostMax; i++) {
            int octet1 = (i >> 24) & 0xFF;
            int octet2 = (i >> 16) & 0xFF;
            int octet3 = (i >> 8) & 0xFF;
            int octet4 = i & 0xFF;

            Ippool ip = new Ippool();
            // 将网关掩码存入
            ip.setNodeId(nodeId);
            ip.setGateway(gateway);
            ip.setSubnetMask(subnetMaskString);
            ip.setDns1(dns1);
            ip.setDns2(dns2);
            ip.setPoolId(ipParams.getPoolId());
            // 如果ip地址为网关地址，则设置为3,0=正常;1=已使用;2=停用;3=网关
            if (i == gatewayIP) {
                ip.setStatus(3);
            } else {
                ip.setStatus(0);
            }
            ip.setIp(octet1 + "." + octet2 + "." + octet3 + "." + octet4);
            ipList.add(ip);
        }
        return ipList;
    }

    /**
    * @Author: mryunqi
    * @Description: 将掩码位转换为字符串类型
    * @DateTime: 2023/7/3 23:38
    */
    public static String getMaskToString(Integer mask){
        // 计算子网掩码
        int subnetMask = 0xFFFFFFFF << (32 - mask);
        // 将子网掩码转换为字符串形式
        return ((subnetMask >> 24) & 0xFF) + "." +
                ((subnetMask >> 16) & 0xFF) + "." +
                ((subnetMask >> 8) & 0xFF) + "." +
                (subnetMask & 0xFF);
    }

    /**
     * 获取指定IP范围内的所有IP地址
     *
     * @param startIP 起始IP地址
     * @param endIP   结束IP地址
     * @return 包含指定IP范围内所有IP地址的List
     */
    public static List<String> getAllIPsInRange(String startIP, String endIP) {
        List<String> ipList = new ArrayList<>();

        long start = ipToLong(startIP);
        long end = ipToLong(endIP);

        for (long i = start; i <= end; i++) {
            ipList.add(longToIp(i));
        }

        return ipList;
    }

    /**
     * 将IP地址转换为长整型表示
     *
     * @param ipAddress IP地址
     * @return IP地址的长整型表示
     */
    private static long ipToLong(String ipAddress) {
        String[] ipAddressInArray = ipAddress.split("\\.");

        long result = 0;
        for (int i = 0; i < ipAddressInArray.length; i++) {
            int power = 3 - i;
            int ip = Integer.parseInt(ipAddressInArray[i]);
            result += ip * Math.pow(256, power);
        }

        return result;
    }

    /**
     * 将长整型表示的IP地址转换为标准IP地址格式
     *
     * @param ip 长整型表示的IP地址
     * @return 标准IP地址格式
     */
    private static String longToIp(long ip) {
        StringBuilder sb = new StringBuilder();

        for (int i = 3; i >= 0; i--) {
            long mask = 0xFFL << (i * 8);
            sb.append((ip & mask) >> (i * 8));

            if (i > 0) {
                sb.append(".");
            }
        }

        return sb.toString();
    }
}
