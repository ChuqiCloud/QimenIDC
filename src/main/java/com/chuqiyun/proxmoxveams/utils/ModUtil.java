package com.chuqiyun.proxmoxveams.utils;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;

/**
 * @author mryunqi
 * @date 2023/5/20
 */
public class ModUtil {
    /**
    * @Author: mryunqi
    * @Description: 获取域名的一级域名
    * @DateTime: 2023/5/20 20:37
    * @Params: String domain
    * @Return String domain
    */
    public static String getTopLevelDomain(String domain) {
        String[] domainParts = domain.split("\\.");
        if (domainParts.length >= 2) {
            return domainParts[domainParts.length - 2] + "." + domainParts[domainParts.length - 1];
        } else {
            return "";
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 判断对象中属性值是否全为空
    * @DateTime: 2023/5/20 20:43
    */
    public static boolean isNull(Object object) {
        if (null == object) {
            return true;
        }

        try {
            for (Field f : object.getClass().getDeclaredFields()) {
                f.setAccessible(true);

                if (f.get(object) != null && StringUtils.isNotBlank(f.get(object).toString())) {
                    return false;
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }
    /**
    * @Author: mryunqi
    * @Description: 将JSONObejct分页
    * @DateTime: 2023/7/14 21:39
    * @Params: JSONObject jsonObject 原始jsonObject
     *         Integer page 页码
     *         Integer size 每页大小
    * @Return JSONObject newJsonObject 分页后的jsonObject
    */
    public static JSONObject jsonObjectPage(JSONObject jsonObject,Integer page,Integer size){
        JSONObject newJsonObject = new JSONObject();
        newJsonObject.put("page",page);
        newJsonObject.put("size",size);
        newJsonObject.put("total",jsonObject.size());
        // 可分几页
        int totalPage = jsonObject.size()/size;
        // 不能整除，多一页
        if (jsonObject.size()%size!=0){
            totalPage++;
        }
        newJsonObject.put("totalPage",totalPage);
        int start = (page-1)*size;
        int end = page*size;
        int i = 0;
        // 创建一个新的jsonObject
        JSONArray data = new JSONArray();
        for (String key : jsonObject.keySet()) {
            if (i>=start && i<end){
                JSONObject jsonData = new JSONObject();
                jsonData.put(key,jsonObject.get(key));
                data.add(jsonData);
            }
            i++;
        }
        newJsonObject.put("data",data);
        return newJsonObject;
    }

    /**
    * @Author: mryunqi
    * @Description: 根据下载url获取文件大小
    * @DateTime: 2023/7/22 17:12
    * @Params: String url 下载url
    * @Return Long fileSize 文件大小
    */
    public static Long getUrlFileSize(String url){
        long fileSize = 0L;
        try {
            java.net.URL urlFile = new java.net.URL(url);
            java.net.HttpURLConnection httpUrl = (java.net.HttpURLConnection) urlFile.openConnection();
            httpUrl.connect();
            fileSize = httpUrl.getContentLengthLong();
            httpUrl.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fileSize;
    }

    /**
    * @Author: mryunqi
    * @Description: 将文件大小转换为可读性更好的格式
    * @DateTime: 2023/7/22 17:17
    * @Params: Long fileSize 文件大小
    * @Return String fileSizeString 可读性更好的文件大小
    */
    public static String formatFileSize(Long fileSize){
        String fileSizeString;
        if (fileSize < 1024) {
            fileSizeString = fileSize + "B";
        } else if (fileSize < 1048576) {
            fileSizeString = fileSize / 1024 + "KB";
        } else if (fileSize < 1073741824) {
            fileSizeString = fileSize / 1048576 + "MB";
        } else {
            fileSizeString = fileSize / 1073741824 + "GB";
        }
        return fileSizeString;
    }

    /**
    * @Author: mryunqi
    * @Description: 随机生成8位字母数字组合密码
    * @DateTime: 2023/8/4 23:38
    * @Return String password 随机生成的密码
    */
    public static String randomPassword(){
        StringBuilder password = new StringBuilder();
        String[] str = {"0","1","2","3","4","5","6","7","8","9",
                "a","b","c","d","e","f","g","h","i","j","k","l","m",
                "n","o","p","q","r","s","t","u","v","w","x","y","z"};
        for (int i=0;i<8;i++){
            int index = (int)(Math.random()*str.length);
            password.append(str[index]);
        }
        return password.toString();
    }

    /**
    * @Author: mryunqi
    * @Description: 将IP地址的.替换为-
    * @DateTime: 2023/8/7 16:13
    * @Params: String ip
    * @Return String ip
    */
    public static String ipReplace(String ip){
        return ip.replace(".","-");
    }
}
