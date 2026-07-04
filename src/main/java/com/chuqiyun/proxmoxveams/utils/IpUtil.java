package com.chuqiyun.proxmoxveams.utils;

import com.chuqiyun.proxmoxveams.dto.IpParams;
import com.chuqiyun.proxmoxveams.entity.Ippool;
import com.chuqiyun.proxmoxveams.entity.Subnetpool;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * @author mryunqi
 * @date 2023/7/2
 */
public class IpUtil {
    private static final int IPV4 = 4;
    private static final int IPV6 = 6;
    private static final int IPV6_MAX_AUTO_GENERATE = 65536;

    /**
    * @Author: mryunqi
    * @Description: 根据网关及掩码位计算所有ip地址
    * @DateTime: 2023/7/2 20:29
    * @Params: IpParams ipParams
    * @Return List<Ippool>
    */
    public static List<Ippool> getIpList(IpParams ipParams){
        if (getIpVersion(ipParams.getIpVersion()) == IPV6) {
            return getIpv6List(ipParams);
        }
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
            ip.setIpVersion(IPV4);
            ip.setGateway(gateway);
            ip.setSubnetMask(subnetMaskString);
            ip.setDns1(dns1);
            ip.setDns2(dns2);
            ip.setMac(generateRandomMacAddress()); // 随机生成mac地址
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

    public static List<Ippool> getIpv6List(IpParams ipParams) {
        String gateway = normalizeIpv6(ipParams.getGateway());
        Integer mask = ipParams.getMask();
        Integer nodeId = ipParams.getNodeId();
        String dns1 = ipParams.getDns1();
        String dns2 = ipParams.getDns2();
        if (mask == null || mask < 1 || mask > 128) {
            throw new IllegalArgumentException("IPv6掩码位不合法");
        }

        BigInteger gatewayValue = ipv6ToBigInteger(gateway);
        BigInteger networkAddress = gatewayValue.and(ipv6Mask(mask));
        BigInteger hostCount = BigInteger.ONE.shiftLeft(128 - mask);
        if (hostCount.compareTo(BigInteger.valueOf(IPV6_MAX_AUTO_GENERATE)) > 0) {
            throw new IllegalArgumentException("IPv6掩码范围过大，请使用起止IPv6范围添加地址池");
        }

        BigInteger start = networkAddress;
        BigInteger end = networkAddress.add(hostCount).subtract(BigInteger.ONE);
        return buildIpv6IppoolList(ipParams.getPoolId(), nodeId, gateway, mask, dns1, dns2, start, end, gatewayValue);
    }

    /**
    * @Author: mryunqi
    * @Description: 根据网关及掩码位计算所有ip地址 nat
    * @DateTime: 2024/1/26 15:21
    * @Params: IpParams ipParams
    * @Return List<Subnatpool>
    */
    public static List<Subnetpool> getNatIpList(IpParams ipParams){
        String gateway = ipParams.getGateway();
        Integer mask = ipParams.getMask();
        Integer nodeId = ipParams.getNodeId();
        String dns1 = ipParams.getDns1();
        List<Subnetpool> ipList = new ArrayList<>();
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

            Subnetpool ip = new Subnetpool();
            // 将网关掩码存入
            ip.setNodeId(nodeId);
            ip.setGateway(gateway);
            ip.setMask(mask);
            ip.setDns(dns1);
            ip.setMac(generateRandomMacAddress()); // 随机生成mac地址
            ip.setSubnatId(ipParams.getSubnetId());
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
        if (isValidIpv6(startIP) || isValidIpv6(endIP)) {
            return getAllIpv6InRange(startIP, endIP);
        }
        List<String> ipList = new ArrayList<>();

        long start = ipToLong(startIP);
        long end = ipToLong(endIP);

        for (long i = start; i <= end; i++) {
            ipList.add(longToIp(i));
        }

        return ipList;
    }

    public static List<String> getAllIpv6InRange(String startIP, String endIP) {
        BigInteger start = ipv6ToBigInteger(startIP);
        BigInteger end = ipv6ToBigInteger(endIP);
        if (start.compareTo(end) > 0) {
            throw new IllegalArgumentException("IPv6起始地址不能大于结束地址");
        }
        BigInteger count = end.subtract(start).add(BigInteger.ONE);
        if (count.compareTo(BigInteger.valueOf(IPV6_MAX_AUTO_GENERATE)) > 0) {
            throw new IllegalArgumentException("IPv6范围过大，单次最多添加" + IPV6_MAX_AUTO_GENERATE + "个地址");
        }
        List<String> ipList = new ArrayList<>();
        for (BigInteger current = start; current.compareTo(end) <= 0; current = current.add(BigInteger.ONE)) {
            ipList.add(bigIntegerToIpv6(current));
        }
        return ipList;
    }

    public static boolean isValidIp(String ip, Integer ipVersion) {
        return getIpVersion(ipVersion) == IPV6 ? isValidIpv6(ip) : isValidIpv4(ip);
    }

    public static boolean isValidIpv4(String ip) {
        if (ip == null || !ip.matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")) {
            return false;
        }
        for (String part : ip.split("\\.")) {
            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidIpv6(String ip) {
        if (ip == null || !ip.contains(":")) {
            return false;
        }
        try {
            return InetAddress.getByName(ip).getAddress().length == 16;
        } catch (Exception e) {
            return false;
        }
    }

    public static int getIpVersion(Integer ipVersion) {
        return ipVersion != null && ipVersion == IPV6 ? IPV6 : IPV4;
    }

    public static String normalizeIpv6(String ip) {
        return bigIntegerToIpv6(ipv6ToBigInteger(ip));
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

    /**
     * 生成随机MAC地址
     *
     * @return 随机MAC地址
     */
    public static String generateRandomMacAddress() {
        Random random = new Random();
        byte[] macAddressBytes = new byte[6];
        random.nextBytes(macAddressBytes);

        // 将第一个字节的最低有效位设置为0（单播）
        macAddressBytes[0] = (byte) (macAddressBytes[0] & (byte) 254);

        StringBuilder macAddress = new StringBuilder(18);
        for (byte b : macAddressBytes) {
            if (macAddress.length() > 0) {
                macAddress.append(":");
            }
            macAddress.append(String.format("%02X", b));
        }

        return macAddress.toString();
    }

    private static List<Ippool> buildIpv6IppoolList(Integer poolId, Integer nodeId, String gateway, Integer mask,
                                                    String dns1, String dns2, BigInteger start, BigInteger end,
                                                    BigInteger gatewayValue) {
        List<Ippool> ipList = new ArrayList<>();
        for (BigInteger current = start; current.compareTo(end) <= 0; current = current.add(BigInteger.ONE)) {
            Ippool ip = new Ippool();
            ip.setNodeId(nodeId);
            ip.setIpVersion(IPV6);
            ip.setGateway(gateway);
            ip.setSubnetMask(String.valueOf(mask));
            ip.setDns1(dns1);
            ip.setDns2(dns2);
            ip.setMac(generateRandomMacAddress());
            ip.setPoolId(poolId);
            ip.setStatus(current.equals(gatewayValue) ? 3 : 0);
            ip.setIp(bigIntegerToIpv6(current));
            ipList.add(ip);
        }
        return ipList;
    }

    private static BigInteger ipv6Mask(int prefixLength) {
        BigInteger all = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);
        return all.shiftRight(128 - prefixLength).shiftLeft(128 - prefixLength);
    }

    private static BigInteger ipv6ToBigInteger(String ip) {
        try {
            byte[] bytes = InetAddress.getByName(ip).getAddress();
            if (bytes.length != 16) {
                throw new IllegalArgumentException("IPv6地址不合法: " + ip);
            }
            return new BigInteger(1, bytes);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("IPv6地址不合法: " + ip, e);
        }
    }

    private static String bigIntegerToIpv6(BigInteger value) {
        byte[] src = value.toByteArray();
        byte[] bytes = new byte[16];
        int srcPos = Math.max(0, src.length - 16);
        int length = Math.min(src.length, 16);
        System.arraycopy(src, srcPos, bytes, 16 - length, length);
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("IPv6地址转换失败", e);
        }
    }
}
