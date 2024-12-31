package com.chuqiyun.proxmoxveams.utils;

import com.alibaba.fastjson2.JSONObject;
import io.swagger.models.auth.In;
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
    public static JSONObject getControllerConnectStatus(String ip, Integer port,String token){
        String url = "http://"+ip+":"+port+"/status";
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
    public static Boolean downloadFile(String ip, Integer port, String token, String fileUrl,String path){
        String url = "http://"+ip+":"+port+"/wget";
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
    public static JSONObject getDownloadProgress(String ip, Integer port, String token, String fileUrl,String path){
        String url = "http://"+ip+":"+port+"/wget";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("url",fileUrl);
        paramMap.put("path",path);
        return getControllerApi(url, paramMap, token);
    }

    /**
    * @Author: mryunqi
    * @Description: 删除os文件
    * @DateTime: 2023/8/15 19:48
    * @Params: ip 被控端ip
     * @Params: token 被控端token
     * @Params: osName os文件名
    * @Return Boolean 是否删除成功
    */
    public static Boolean deleteOsFile(String ip, Integer port, String token, String osName){
        String url = "http://"+ip+":"+port+"/deleteFile";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("path","/home/images/");
        paramMap.put("file",osName);
        JSONObject result = postControllerApi(url, paramMap, token);
        return result != null && result.getInteger("code") == 200;
    }

    /**
    * @Author: mryunqi
    * @Description: 重置密码
    * @DateTime: 2023/9/2 15:18
    * @Params:
    * @Return
    */
    public static Boolean resetPassword(String ip,Integer port, String token, Integer vmId, String username, String password){
        String url = "http://"+ip+":"+port+"/changePassword";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("id",vmId);
        paramMap.put("username",username);
        paramMap.put("password",password);
        JSONObject result = postControllerApi(url, paramMap, token);
        return result != null && result.getInteger("code") == 200;
    }

    /**
    * @Author: mryunqi
    * @Description: 导入磁盘
    * @DateTime: 2023/9/26 15:07
    * @Params: ip 被控端ip
     * @Params: token 被控端token
     * @Params: vmid 虚拟机id
     * @Params: diskName 磁盘名称
     * @Params: osPath 磁盘路径
    * @Return Boolean 是否导入成功
    */
    public static Boolean importDisk(String ip,Integer port, String token, Long vmid, String osPath,  String diskName){
        String url = "http://"+ip+":"+port+"/importDisk";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("vmid",vmid);
        paramMap.put("image_path",osPath);
        paramMap.put("save_path",diskName);
        JSONObject result = postControllerApi(url, paramMap, token);
        return result != null && result.getInteger("code") == 200;
    }
    
    /**
    * @Author: mryunqi
    * @Description: 获取节点网卡配置信息
    * @DateTime: 2023/10/28 22:00
    * @Params: ip 被控端ip
     * @Params: token 被控端token
    * @Return JSONObject 网卡配置信息
    */
    public static JSONObject getNetworkInfo(String ip,Integer port, String token){
        String url = "http://"+ip+":"+port+"/readFile";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("path","/etc/network");
        paramMap.put("filename","interfaces");
        return postControllerApi(url, paramMap, token);
    }
    
    /**
    * @Author: mryunqi
    * @Description: 创建vnc服务
    * @DateTime: 2023/11/23 18:15
    * @Params: ip 被控端ip
     * @Params: token 被控端token
     * @Params: vmid 虚拟机id
     * @Params: vncPort vnc端口
     * @Params: vncPassword vnc密码
     * @Params: controllerPort 被控端端口
     * @Params: vncHost 被控端ip
     * @Params: time 超时时间
    * @Return Boolean 是否创建成功
    */
    public static Boolean createVncService(String ip, String token, Integer vmid, Integer vncPort,String username, String vncPassword,Integer controllerPort, String vncHost, Integer time){
        String url = "http://"+ip+":"+controllerPort+"/vnc";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("vnc_file_path","/home/software/vnc");
        paramMap.put("vmid",vmid);
        paramMap.put("username",username);
        paramMap.put("password",vncPassword);
        paramMap.put("port",vncPort);
        paramMap.put("host",vncHost);
        paramMap.put("time",time);
        JSONObject result = postControllerApi(url, paramMap, token);
        return result != null && result.getInteger("code") == 200;
    }

    /**
    * @Author: mryunqi
    * @Description: 导入VNC配置信息
    * @DateTime: 2023/11/24 20:48
     * @Params: ip 被控端ip
     * @Params: token 被控端token
     * @Params: vmid 虚拟机id
     * @Params: vncPort vnc端口
     * @Params: vncPassword vnc密码
     * @Params: controllerPort 被控端端口
     * @Params: vncHost 被控端ip
     * @Params: time 超时时间
    * @Return Boolean 是否导入成功
    */
    public static Boolean importVncService(String ip, String token, Integer vmid, Integer vncPort,String username, String vncPassword,Integer controllerPort, String vncHost, Integer time){
        String url = "http://"+ip+":"+controllerPort+"/vnc/import";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("vnc_file_path","/home/software/vnc");
        paramMap.put("vmid",vmid);
        paramMap.put("username",username);
        paramMap.put("password",vncPassword);
        paramMap.put("port",vncPort);
        paramMap.put("host",vncHost);
        paramMap.put("time",time);
        JSONObject result = postControllerApi(url, paramMap, token);
        return result != null && result.getInteger("code") == 200;
    }

    /**
    * @Author: mryunqi
    * @Description: 停止指定VNC服务
    * @DateTime: 2023/11/24 22:35
    * @Params: ip 被控端ip
     * @Params: token 被控端token
     * @Params: vncPort vnc端口
     * @Params: controllerPort 被控端端口
    * @Return Boolean 是否停止成功
    */
    public static Boolean stopVncService(String ip, String token, Integer vncPort,Integer controllerPort){
        String url = "http://"+ip+":"+controllerPort+"/vnc/stop";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("vnc_file_path","/home/software/vnc");
        paramMap.put("vmid",null);
        paramMap.put("username",null);
        paramMap.put("password",null);
        paramMap.put("port",vncPort);
        paramMap.put("host",null);
        paramMap.put("time",null);
        JSONObject result = postControllerApi(url, paramMap, token);
        return result != null && result.getInteger("code") == 200;
    }

    /**
    * @Author: mryunqi
    * @Description: 重启宿主机网络
    * @DateTime: 2024/1/20 15:39
    * @Params: ip 被控端ip
     * @Params: token 被控端token
     * @Params: controllerPort 被控端端口
    * @Return  Boolean 是否重启成功
    */
    public static Boolean restartNetwork(String ip, String token, Integer controllerPort){
        String url = "http://"+ip+":"+controllerPort+"/restartNetwork";
        Map<String, Object> paramMap = new HashMap<>();
        JSONObject result = postControllerApi(url, paramMap, token);
        return result != null && result.getInteger("code") == 200;
    }

    /**
    * @Author: mryunqi
    * @Description: 添加端口转发
    * @DateTime: 2024/1/20 15:41
    * @Params: ip 被控端ip
     * @Params: token 被控端token
     * @Params: controllerPort 被控端端口
     * @Params: hostId 虚拟机id
     * @Params: source_port 源端口
     * @Params: destination_ip 目标ip
     * @Params: destination_port 目标端口
     * @Params: protocol 协议
    * @Return Boolean 是否添加成功
    */
    public static Boolean addPortForward(String ip, String token, Integer controllerPort, Integer hostId, Integer source_port, String destination_ip, Integer destination_port, String protocol){
        String url = "http://"+ip+":"+controllerPort+"/nat/add";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("vm",hostId);
        paramMap.put("source_port",source_port);
        paramMap.put("destination_ip",destination_ip);
        paramMap.put("destination_port",destination_port);
        paramMap.put("protocol",protocol);
        JSONObject result = postControllerApi(url, paramMap, token);
        return result != null && result.getInteger("code") == 200;
    }

    /**
    * @Author: mryunqi
    * @Description: 删除端口转发
    * @DateTime: 2024/1/20 15:44
    * @Params: ip 被控端ip
     * @Params: token 被控端token
     * @Params: controllerPort 被控端端口
     * @Params: hostId 虚拟机id
     * @Params: source_port 源端口
     * @Params: destination_ip 目标ip
     * @Params: destination_port 目标端口
     * @Params: protocol 协议
    * @Return  Boolean 是否删除成功
    */
    public static Boolean deletePortForward(String ip, String token, Integer controllerPort, Integer hostId, Integer source_port, String destination_ip, Integer destination_port, String protocol) {
        String url = "http://" + ip + ":" + controllerPort + "/nat/delete";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("vm", hostId);
        paramMap.put("source_port", source_port);
        paramMap.put("destination_ip", destination_ip);
        paramMap.put("destination_port", destination_port);
        paramMap.put("protocol", protocol);
        JSONObject result = postControllerApi(url, paramMap, token);
        return result != null && result.getInteger("code") == 200;
    }

    /**
    * @Author: mryunqi
    * @Description: 分页查询指定虚拟机主机的端口转发
    * @DateTime: 2024/1/20 15:45
    * @Params: ip 被控端ip
     * @Params: token 被控端token
     * @Params: controllerPort 被控端端口
     * @Params: hostId 虚拟机id
     * @Params: page 页码
     * @Params: size 每页条数
    * @Return JSONObject 端口转发列表
    */
    public static JSONObject getPortForwardList(String ip, String token, Integer controllerPort, Integer hostId, Integer page, Integer size){
        String url = "http://"+ip+":"+controllerPort+"/nat/getVm/"+page+"/"+size;
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("vm",hostId);
        JSONObject result = getControllerApi(url, paramMap, token);
        return result != null && result.getInteger("code") == 200 ? result : null;
    }
}
