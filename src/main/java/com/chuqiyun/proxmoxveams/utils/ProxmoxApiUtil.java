package com.chuqiyun.proxmoxveams.utils;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.config.RestTemplateConfig;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.common.exception.UnauthorizedException;
import org.springframework.http.*;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author mryunqi
 * @date 2023/6/18
 */
public class ProxmoxApiUtil {
    /**
    * @Author: mryunqi
    * @Description: 获取节点url
    * @DateTime: 2023/6/20 15:26
    * @Params: Master node
    * @Return String
    */
    public String getNodeUrl(Master node) {
        return "https://" + node.getHost() + ":" + node.getPort() + "/api2/json";
    }

    /**
    * @Author: mryunqi
    * @Description: 通用POST请求
    * @DateTime: 2023/6/20 16:33
    * @Params: Master node, HashMap<String,String> cookie,String url, HashMap<String, String> params
    * @Return  JSONObject
    */
    public JSONObject postNodeApi(Master node, HashMap<String,String> cookie,String url, HashMap<String, Object> params) throws UnauthorizedException {
        // 构建请求主体
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Cookie", cookie.get("cookie"));
        headers.add("CSRFPreventionToken", cookie.get("CSRFPreventionToken"));

        Map<String, Object> requestBody = new HashMap<>();
        for (String key : params.keySet()) {
            requestBody.put(key, params.get(key));
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        // 忽略证书验证
        TrustSslUtil.initDefaultSsl();
        // 发送 POST 请求
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(getNodeUrl(node) + url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            // 提取 Cookie 和 CSRF 预防令牌
            JSONObject body = JSONObject.parseObject(response.getBody());
            assert body != null;
            return body;
        } else {
            throw new UnauthorizedException("请求失败");
        }
    }

    /**
    * @Author: mryunqi
    * @Description: post通用form请求
    * @DateTime: 2024/1/17 21:00
    * @Params: Master node, HashMap<String,String> cookie,String url, HashMap<String, String> params
    * @Return JSONObject
    */
    public JSONObject postNodeApiForm(Master node, HashMap<String,String> cookie, String url, HashMap<String, Object> params) throws UnauthorizedException {
        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add("Cookie", cookie.get("cookie"));
        headers.add("CSRFPreventionToken", cookie.get("CSRFPreventionToken"));

        // 将参数转换为表单格式（MultiValueMap）
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        for (String key : params.keySet()) {
            Object value = params.get(key);
            formData.add(key, value != null ? value.toString() : "");
        }

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(formData, headers);
        TrustSslUtil.initDefaultSsl();
        RestTemplate restTemplate = createRestTemplateWithFormSupport();
        ResponseEntity<String> response = restTemplate.exchange(getNodeUrl(node) + url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            // 提取 Cookie 和 CSRF 预防令牌
            JSONObject body = JSONObject.parseObject(response.getBody());
            assert body != null;
            return body;
        } else {
            throw new UnauthorizedException("请求失败");
        }
    }

    // 创建配置了表单支持的 RestTemplate
    private RestTemplate createRestTemplateWithFormSupport() {
        RestTemplate restTemplate = new RestTemplate();

        // 添加支持表单数据的消息转换器
        List<HttpMessageConverter<?>> messageConverters = new ArrayList<>();
        messageConverters.add(new FormHttpMessageConverter());
        messageConverters.add(new StringHttpMessageConverter());

        restTemplate.setMessageConverters(messageConverters);
        return restTemplate;
    }

    /**
    * @Author: mryunqi
    * @Description: 通用GET请求
    * @DateTime: 2023/6/20 16:39
    */
    public JSONObject getNodeApi(Master node, HashMap<String,String> cookie,String url, HashMap<String, Object> params) throws UnauthorizedException {
        // 构建请求主体
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Cookie", cookie.get("cookie"));
        headers.add("CSRFPreventionToken", cookie.get("CSRFPreventionToken"));

        Map<String, Object> requestBody = new HashMap<>();
        for (String key : params.keySet()) {
            requestBody.put(key, params.get(key));
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        // 忽略证书验证
        TrustSslUtil.initDefaultSsl();
        // 发送 POST 请求
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(getNodeUrl(node) + url, HttpMethod.GET, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            // 提取 Cookie 和 CSRF 预防令牌
            JSONObject body = JSONObject.parseObject(response.getBody());
            assert body != null;
            return body;
        } else {
            throw new UnauthorizedException("请求失败");
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 通用PUT请求
    * @DateTime: 2023/6/20 23:00
    */
    public JSONObject putNodeApi(Master node, HashMap<String,String> cookie,String url, HashMap<String, Object> params) throws UnauthorizedException {
        // 构建请求主体
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Cookie", cookie.get("cookie"));
        headers.add("CSRFPreventionToken", cookie.get("CSRFPreventionToken"));

        Map<String, Object> requestBody = new HashMap<>();
        for (String key : params.keySet()) {
            requestBody.put(key, params.get(key));
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        // 忽略证书验证
        TrustSslUtil.initDefaultSsl();
        // 发送 POST 请求
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(getNodeUrl(node) + url, HttpMethod.PUT, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            // 提取 Cookie 和 CSRF 预防令牌
            JSONObject body = JSONObject.parseObject(response.getBody());
            assert body != null;
            return body;
        } else {
            throw new UnauthorizedException("请求失败");
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 通用DELETE请求
    * @DateTime: 2023/9/2 15:46
    * @Params: Master node, HashMap<String,String> cookie,String url, HashMap<String, Object> params
    * @Return  JSONObject
    */
    public JSONObject deleteNodeApi(Master node, HashMap<String, String> cookie, String url, HashMap<String, Object> params) throws UnauthorizedException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Cookie", cookie.get("cookie"));
        headers.add("CSRFPreventionToken", cookie.get("CSRFPreventionToken"));

        HttpEntity<?> entity = new HttpEntity<>(headers);

        // 忽略证书验证
        TrustSslUtil.initDefaultSsl();

        // 发送 DELETE 请求
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(getNodeUrl(node) + url, HttpMethod.DELETE, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            JSONObject body = JSONObject.parseObject(response.getBody());
            assert body != null;
            return body;
        } else {
            throw new UnauthorizedException("请求失败");
        }
    }


    /**
    * @Author: mryunqi
    * @Description: 登录获取cookie
    * @DateTime: 2023/6/20 15:22
    * @Return
    */
    public HashMap<String, String> loginAndGetCookie(HashMap<String,String> user) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        RestTemplate restTemplate = new RestTemplate(RestTemplateConfig.generateHttpRequestFactory());
        // 构建请求主体
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> requestBody = new HashMap<>();

        requestBody.put("username", user.get("username"));
        requestBody.put("password", user.get("password"));
        requestBody.put("realm", user.get("realm"));

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);
        // 忽略证书验证
        TrustSslUtil.initDefaultSsl();
        // 发送 POST 请求
        ResponseEntity<String> response = restTemplate.exchange(user.get("url") + "/api2/json/access/ticket", HttpMethod.POST, entity, String.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            // 提取 Cookie 和 CSRF 预防令牌
            JSONObject body = JSONObject.parseObject(response.getBody());
            assert body != null;
            String ticket = body.getJSONObject("data").getString("ticket");
            String csrfToken = body.getJSONObject("data").getString("CSRFPreventionToken");

            // 将 Cookie 和 CSRF 预防令牌存储起来，以便后续使用
            HashMap<String, String> authenticationTokens = new HashMap<>();
            authenticationTokens.put("ticket",ticket);
            authenticationTokens.put("csrfToken",csrfToken);
            return authenticationTokens;
        } else {
            throw new UnauthorizedException("Login failed");
        }
    }

    /**
    * @Author: mryunqi
    * @Description: 获取单节点状态信息
    * @DateTime: 2023/8/5 21:27
    * @Params: Master node 节点信息 HashMap<String,String> cookie 登录信息
    * @Return JSONObject 节点状态信息
    */
    public JSONObject getNodeStatusByOne(Master node, HashMap<String,String> cookie) throws UnauthorizedException {
        return getNodeApi(node,cookie,"/nodes/" + node.getNodeName() + "/status",new HashMap<>());
    }

    /**
    * @Author: mryunqi
    * @Description: 获取指定节点负载信息
    * @DateTime: 2023/8/6 15:15
    * @Params:  Master node 节点信息 HashMap<String,String> cookie 登录信息 String timeframe 时间范围 String cf 数据源
    * @Return JSONObject 节点负载信息
    */
    public JSONObject getNodeLoadAvg(Master node, HashMap<String,String> cookie,String timeframe,String cf) throws UnauthorizedException {
        return getNodeApi(node,cookie,"/nodes/" + node.getNodeName() + "/rrddata?timeframe="+timeframe+"&cf="+cf,new HashMap<>());
    }

    /**
    * @Author: mryunqi
    * @Description: 获取指定节点网络信息
    * @DateTime: 2023/8/18 15:05
    * @Params: Master node 节点信息 HashMap<String,String> cookie 登录信息 HashMap<String,Object> params 请求参数
    * @Return JSONObject 节点网络信息
    */
    public JSONObject getNodeNet(Master node, HashMap<String,String> cookie,HashMap<String,Object> params) throws UnauthorizedException {
        return getNodeApi(node,cookie,"/nodes/" + node.getNodeName() + "/network",params);
    }

    /**
    * @Author: mryunqi
    * @Description: 获取指定节点指定虚拟机实时信息
    * @DateTime: 2023/8/24 0:16
    * @Params: Master node 节点信息 HashMap<String,String> cookie 登录信息 String vmid 虚拟机ID
    * @Return JSONObject 虚拟机实时信息
    */
    public JSONObject getVmStatus(Master node, HashMap<String,String> cookie, Integer vmid) throws UnauthorizedException {
        return getNodeApi(node,cookie,"/nodes/" + node.getNodeName() + "/qemu/" + vmid + "/status/current",new HashMap<>());
    }

    /**
    * @Author: mryunqi
    * @Description: 获取指定虚拟机rrd数据
    * @DateTime: 2023/8/28 19:52
    * @Params: Master node 节点信息 HashMap<String,String> cookie 登录信息 Integer vmid 虚拟机ID String timeframe 时间范围 String cf 数据源
    * @Return JSONObject 虚拟机rrd数据
    */
    public JSONObject getVmRrd(Master node, HashMap<String,String> cookie, Integer vmid, String timeframe, String cf) throws UnauthorizedException {
        return getNodeApi(node,cookie,"/nodes/" + node.getNodeName() + "/qemu/" + vmid + "/rrddata?timeframe="+timeframe+"&cf="+cf,new HashMap<>());
    }

    /**
    * @Author: mryunqi
    * @Description: 分离并删除虚拟机磁盘
    * @DateTime: 2023/9/1 20:07
    * @Params: Master node 节点信息 HashMap<String,String> cookie 登录信息 Integer vmid 虚拟机ID String disk 磁盘名称
    * @Return Boolean 是否成功
    */
    public void deleteVmDisk(Master node, HashMap<String,String> cookie, Integer vmid, String disk) throws UnauthorizedException {
        // 分离磁盘
        HashMap<String,Object> params = new HashMap<>();
        params.put("delete",disk);
        putNodeApi(node,cookie,"/nodes/" + node.getNodeName() + "/qemu/" + vmid + "/config",params);
        // 删除磁盘
        params.put("delete","unused0");
        putNodeApi(node,cookie,"/nodes/" + node.getNodeName() + "/qemu/" + vmid + "/config",params);
    }
    
    /**
    * @Author: mryunqi
    * @Description: 重置虚拟机密码
    * @DateTime: 2023/9/2 15:09
    * @Params: Master node 节点信息 HashMap<String,String> cookie 登录信息 Integer vmid 虚拟机ID String password 密码
    */
    public void resetVmPassword(Master node, HashMap<String,String> cookie, Integer vmid, String password) throws UnauthorizedException {
        HashMap<String,Object> params = new HashMap<>();
        params.put("cipassword",password);
        putNodeApi(node,cookie,"/nodes/" + node.getNodeName() + "/qemu/" + vmid + "/config",params);
    }

    /**
    * @Author: mryunqi
    * @Description: 重生成cloudinit镜像
    * @DateTime: 2025/6/28 10:52
    * @Params: Master node 节点信息 HashMap<String,String> cookie 登录信息 Integer vmid 虚拟机ID
    */
    public void resetVmCloudinit(Master node, HashMap<String,String> cookie, Integer vmid) throws UnauthorizedException {
        putNodeApi(node,cookie,"/nodes/" + node.getNodeName() + "/qemu/" + vmid + "/cloudinit",new HashMap<>());
    }
    
    /**
    * @Author: mryunqi
    * @Description: 删除指定虚拟机
    * @DateTime: 2023/9/2 15:45
    * @Params: Master node 节点信息 HashMap<String,String> cookie 登录信息 Integer vmid 虚拟机ID
    */
    public void deleteVm(Master node, HashMap<String,String> cookie, Integer vmid) throws UnauthorizedException {
        deleteNodeApi(node,cookie,"/nodes/" + node.getNodeName() + "/qemu/" + vmid + "?purge=1&destroy-unreferenced-disks=1", new HashMap<>());
    }

    /**
     * @Author: 星禾
     * @Description: 重置虚拟机账号
     * @DateTime: 2025/1/4 23:00
     * @Params: Master node 节点信息 HashMap<String,String> cookie 登录信息 Integer vmid 虚拟机ID String password 密码
     */
    public void resetVmUsername(Master node, HashMap<String,String> cookie, Integer vmid, String username) throws UnauthorizedException {
        HashMap<String,Object> params = new HashMap<>();
        params.put("ciuser",username);
        putNodeApi(node,cookie,"/nodes/" + node.getNodeName() + "/qemu/" + vmid + "/config",params);
    }
    /**
     * @Author: 星禾
     * @Description: 重置虚拟机Pve系统类型
     * @DateTime: 2025/1/4 23:00
     * @Params: Master node 节点信息 HashMap<String,String> cookie 登录信息 Integer vmid 虚拟机ID String password 密码
     */
    public void resetVmOsType(Master node, HashMap<String,String> cookie, Integer vmid, String ostype) throws UnauthorizedException {
        HashMap<String,Object> params = new HashMap<>();
        // 设置虚拟机osType
        params.put("ostype", ostype);
        putNodeApi(node,cookie,"/nodes/" + node.getNodeName() + "/qemu/" + vmid + "/config",params);
    }
    /**
     * @Author: 星禾
     * @Description: 重置虚拟机Pve系统类型
     * @DateTime: 2025/1/4 23:00
     * @Params: Master node 节点信息 HashMap<String,String> cookie 登录信息 Integer vmid 虚拟机ID String password 密码
     */
    public void resetVmCitype(Master node, HashMap<String,String> cookie, Integer vmid, String citype) throws UnauthorizedException {
        HashMap<String,Object> params = new HashMap<>();
        // 设置虚拟机citype
        params.put("citype",citype);
        putNodeApi(node,cookie,"/nodes/" + node.getNodeName() + "/qemu/" + vmid + "/config",params);
    }
    /**
     * @Author: 星禾
     * @Description: 统一接口-重置虚拟机配置
     * @DateTime: 2025/11/26 21:00
     * @Params: Master node 节点信息 HashMap<String,String> cookie 登录信息 Integer vmid 虚拟机ID String password 密码
     */
    public void resetVmConfig(Master node, HashMap<String,String> cookie, Integer vmid, String type, String values) throws UnauthorizedException {
        HashMap<String,Object> params = new HashMap<>();
        // 设置虚拟机citype
        params.put(type,values);
        putNodeApi(node,cookie,"/nodes/" + node.getNodeName() + "/qemu/" + vmid + "/config",params);
    }
}
