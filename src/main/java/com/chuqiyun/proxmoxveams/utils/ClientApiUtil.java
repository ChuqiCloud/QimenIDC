package com.chuqiyun.proxmoxveams.utils;

import com.alibaba.fastjson2.JSONObject;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * @author mryunqi
 * @date 2023/7/8
 */
public class ClientApiUtil {
    // get请求https://gitee.com/chuqicloud/soft-ware/raw/master/Cloud/os.json
    /**
    * @Author: mryunqi
    * @Description: 获取系统镜像下载地址json
    * @DateTime: 2023/7/8 18:12
    * @Params:
    * @Return
    */
    public static JSONObject getNetOs(String url){
        RestTemplate restTemplate = new RestTemplate();
        //请求url获取内容
        String json = restTemplate.getForObject(url, String.class);
        //将json转换为JSONObject对象
        return JSONObject.parseObject(json);
    }

    /**
    * @Author: mryunqi
    * @Description: 通用GET请求
    * @DateTime: 2023/7/11 17:37
    */
    public static JSONObject getControllerApi(String url, Map<String, String> params, String token){
        RestTemplate restTemplate = new RestTemplate();
        // 拼装url参数
        StringBuilder urlBuilder = new StringBuilder(url);
        if (params != null && params.size() > 0) {
            urlBuilder.append("?");
            for (String key : params.keySet()) {
                urlBuilder.append(key).append("=").append(params.get(key)).append("&");
            }
            urlBuilder.deleteCharAt(urlBuilder.length() - 1);
        }
        // 将token放入header
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, token);
        //请求url获取内容
        String json = restTemplate.getForObject(urlBuilder.toString(), String.class, headers);
        //将json转换为JSONObject对象
        return JSONObject.parseObject(json);
    }

    /**
    * @Author: mryunqi
    * @Description: 获取被控端连接状态
    * @DateTime: 2023/7/11 17:45
    */
    public static JSONObject getControllerConnectStatus(String ip, String token){
        String url = "http://"+ip+":7600/status";
        RestTemplate restTemplate = new RestTemplate();
        // 将token放入header
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, token);
        //请求url获取内容
        String json = restTemplate.getForObject(url, String.class, headers);
        //将json转换为JSONObject对象
        return JSONObject.parseObject(json);
    }
}
