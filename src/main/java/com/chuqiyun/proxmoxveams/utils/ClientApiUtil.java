package com.chuqiyun.proxmoxveams.utils;

import com.alibaba.fastjson2.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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
    public static JSONObject getControllerApi(String url, Map<String, Object> params, String token){
        RestTemplate restTemplate = new RestTemplate();
        // 将token放入header
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, token);
        // url拼接参数
        StringBuilder urlBuilder = new StringBuilder(url);
        if (params != null && params.size() > 0) {
            urlBuilder.append("?");
            for (String key : params.keySet()) {
                urlBuilder.append(key).append("={").append(key).append("}&");
            }
            urlBuilder.deleteCharAt(urlBuilder.length() - 1);
        }
        HttpEntity<Object> entity = new HttpEntity<>(params,headers);
        ResponseEntity<JSONObject> result = restTemplate.exchange(urlBuilder.toString(), HttpMethod.GET, entity, JSONObject.class, params);
        return result.getBody();
    }
    /**
    * @Author: mryunqi
    * @Description: 通用POST请求
    * @DateTime: 2023/7/16 17:12
    * @Params: url 请求地址
     * @Params: params 请求参数
     * @Params: token 请求token
    * @Return JSONObject
    */
    public static JSONObject postControllerApi(String url,Map<String,Object> params,String token){
        RestTemplate restTemplate = new RestTemplate();
        // 将token放入header
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, token);
        headers.add("Content-Type","application/json");
        HttpEntity<Object> entity = new HttpEntity<>(params, headers);
        return restTemplate.postForObject(url, entity,JSONObject.class);
    }

    /**
    * @Author: mryunqi
    * @Description: 获取被控端连接状态
    * @DateTime: 2023/7/11 17:45
    */
    public static JSONObject getControllerConnectStatus(String ip, String token){
        String url = "http://"+ip+":7600/status";
        Map<String, Object> paramMap = new HashMap<>();
        RestTemplate restTemplate = new RestTemplate();
        // 将token放入header
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, token);
        HttpEntity<Object> entity = new HttpEntity<>(paramMap,headers);
        ResponseEntity<JSONObject> result = restTemplate.exchange(url, HttpMethod.GET, entity, JSONObject.class, paramMap);
        return result.getBody();
    }
    
    /**
    * @Author: mryunqi
    * @Description: 下载指定url文件到指定路径
    * @DateTime: 2023/7/16 17:16
    * @Params: ip 被控端ip
     * @Params: token 被控端token
     * @Params: fileUrl 文件url
     * @Params: path 文件下载路径
    * @Return Boolean
    */
    public static Boolean downloadFile(String ip, String token, String fileUrl,String path){
        String url = "http://"+ip+":7600/wget";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("url",fileUrl);
        paramMap.put("path",path);
        JSONObject result = getControllerApi(url, paramMap, token);
        return result != null && result.getInteger("code") == 200;
    }

    /**
    * @Author: mryunqi
    * @Description: 获取下载进度
    * @DateTime: 2023/8/14 16:45
    * @Params: ip 被控端ip
     * @Params: token 被控端token
     * @Params: fileUrl 文件url
     * @Params: path 文件下载路径
    * @Return JSONObject 下载进度
    */
    public static JSONObject getDownloadProgress(String ip, String token, String fileUrl,String path){
        String url = "http://"+ip+":7600/wget";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("url",fileUrl);
        paramMap.put("path",path);
        return getControllerApi(url, paramMap, token);
    }
}
