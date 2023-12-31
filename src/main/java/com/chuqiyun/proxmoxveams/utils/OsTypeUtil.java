package com.chuqiyun.proxmoxveams.utils;

/**
 * @author mryunqi
 * @date 2023/6/29
 */
public class OsTypeUtil {
    /**
    * @Author: mryunqi
    * @Description: 模糊匹配操作系统类型
    * @DateTime: 2023/7/22 21:55
    * @Params: String os 操作系统 String type 操作类型
    * @Return String 操作系统类型
    */
    public static String getOsType(String os,String type) {
        if ("windows".equals(type)) {
            // 模糊匹配os是否为Windows XP
            if (os.toLowerCase().contains("xp")) {
                return "wxp";
            }
            // 模糊匹配os是否为Windows 2000
            if (os.toLowerCase().contains("2000")) {
                return "w2k";
            }
            // 模糊匹配os是否为Windows 2003
            if (os.toLowerCase().contains("2003")) {
                return "w2k3";
            }
            // 模糊匹配os是否为Windows 2008
            if (os.toLowerCase().contains("2008")) {
                return "w2k8";
            }
            // 模糊匹配os是否为Windows Vista
            if (os.toLowerCase().contains("vista")) {
                return "wvista";
            }
            // 模糊匹配os是否为Windows 7
            if (os.toLowerCase().contains("7")) {
                return "win7";
            }
            // 模糊匹配os是否为Windows 8
            if (os.toLowerCase().contains("8")) {
                return "win8";
            }
            // 模糊匹配os是否为Windows 2012
            if (os.toLowerCase().contains("2012")) {
                return "win8";
            }
            // 模糊匹配os是否为Windows 10
            if (os.toLowerCase().contains("10")) {
                return "win10";
            }
            // 模糊匹配os是否为Windows 2016
            if (os.toLowerCase().contains("2016")) {
                return "win10";
            }
            // 模糊匹配os是否为Windows 2019
            if (os.toLowerCase().contains("2019")) {
                return "win10";
            }
            // 模糊匹配os是否为Windows 11
            if (os.toLowerCase().contains("11")) {
                return "win11";
            }
            // 模糊匹配os是否为Windows 2022
            if (os.toLowerCase().contains("2022")) {
                return "win11";
            }
        }else if ("linux".equals(type)){
            return "l26";
        }
        return "other";
    }

    /**
    * @Author: mryunqi
    * @Description: 匹配os实体类参数对应数据库字段
    * @DateTime: 2023/7/22 21:56
    * @Params: String param os实体类参数
    * @Return String 数据库字段
    */
    public static String getOsEntityDbName(String param){
        return switch (param) {
            case "name" -> "name";
            case "fileName" -> "file_name";
            case "type" -> "type";
            case "arch" -> "arch";
            case "osType" -> "os_type";
            case "downType" -> "down_type";
            case "url" -> "url";
            case "path" -> "path";
            case "cloud" -> "cloud";
            default -> null;
        };
    }
}
