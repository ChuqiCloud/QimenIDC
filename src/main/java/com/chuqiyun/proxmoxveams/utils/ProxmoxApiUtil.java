package com.chuqiyun.proxmoxveams.utils;

import com.alibaba.fastjson2.JSONArray;
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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
        try {
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
        } catch (ResourceAccessException e) {
            String logError = "Proxmox API请求超时 [URL: "+url+"] [错误码: " +e.getMostSpecificCause().getClass().getSimpleName() +"]";
            System.err.println(logError);
            return null;
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
    public JSONObject deleteNodeApiByUri(Master node, HashMap<String, String> cookie, String encodedUrl) throws UnauthorizedException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Cookie", cookie.get("cookie"));
        headers.add("CSRFPreventionToken", cookie.get("CSRFPreventionToken"));

        HttpEntity<?> entity = new HttpEntity<>(headers);

        TrustSslUtil.initDefaultSsl();

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(URI.create(getNodeUrl(node) + encodedUrl), HttpMethod.DELETE, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            JSONObject body = JSONObject.parseObject(response.getBody());
            assert body != null;
            return body;
        } else {
            throw new UnauthorizedException("请求失败");
        }
    }

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
     * @Author: 鏄熺
     * @Description: 强制关闭指定虚拟机
     * @DateTime: 2026/5/29 23:03
     * @Params: Master node 节点信息 HashMap<String,String> cookie 登录信息 Integer vmid 虚拟机ID
     * @Return JSONObject
     */
    public JSONObject forceStopVm(Master node, HashMap<String,String> cookie, Integer vmid) throws UnauthorizedException {
        HashMap<String,Object> params = new HashMap<>();
        return postNodeApi(node, cookie, "/nodes/" + node.getNodeName() + "/qemu/" + vmid + "/status/stop", params);
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

    public void deleteVmConfig(Master node, HashMap<String,String> cookie, Integer vmid, String type) throws UnauthorizedException {
        HashMap<String,Object> params = new HashMap<>();
        params.put("delete", type);
        putNodeApi(node,cookie,"/nodes/" + node.getNodeName() + "/qemu/" + vmid + "/config",params);
    }

    public JSONObject guestExec(Master node, HashMap<String,String> cookie, Integer vmid, String command,
                                List<String> args) throws UnauthorizedException {
        HashMap<String,Object> params = new HashMap<>();
        params.put("command", buildGuestExecCommand(command, args));
        return postNodeApi(node, cookie, "/nodes/" + node.getNodeName() + "/qemu/" + vmid + "/agent/exec", params);
    }

    private String buildGuestExecCommand(String command, List<String> args) {
        StringBuilder builder = new StringBuilder(command == null ? "" : command);
        if (args == null || args.isEmpty()) {
            return builder.toString();
        }
        for (String arg : args) {
            builder.append(' ').append(quoteCommandArg(arg));
        }
        return builder.toString();
    }

    private String quoteCommandArg(String arg) {
        if (arg == null) {
            return "\"\"";
        }
        if (!arg.contains(" ") && !arg.contains("\"")) {
            return arg;
        }
        return "\"" + arg.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
    /**
     * @Author: 星禾
     * @Description: 获取指定虚拟机快照
     * @DateTime: 2026/5/24 17:52
     * @Params: Master node 节点信息 HashMap<String,String> cookie 登录信息 Integer vmid 虚拟机ID
     * @Return JSONObject
     */
    public JSONObject getVmSnapShot(Master node, HashMap<String,String> cookie, Integer vmid) throws UnauthorizedException {
        return getNodeApi(node,cookie,"/nodes/" + node.getNodeName() + "/qemu/" + vmid + "/snapshot",new HashMap<>());
    }

    public JSONObject getNodeTasks(Master node, HashMap<String,String> cookie) throws UnauthorizedException {
        return getNodeApi(node, cookie, "/nodes/" + node.getNodeName() + "/tasks?source=active", new HashMap<>());
    }

    public JSONObject getVmActiveTasks(Master node, HashMap<String,String> cookie, Integer vmid) throws UnauthorizedException {
        return getNodeApi(node, cookie, "/nodes/" + node.getNodeName() + "/tasks?source=active&vmid=" + vmid, new HashMap<>());
    }

    /**
     * @Author: 星禾
     * @Description: 获取指定虚拟机配置
     * @DateTime: 2026/6/7 22:47
     */
    public JSONObject getVmConfig(Master node, HashMap<String,String> cookie, Integer vmid) throws UnauthorizedException {
        JSONObject result = getNodeApi(node, cookie, "/nodes/" + node.getNodeName() + "/qemu/" + vmid + "/config", new HashMap<>());
        return result == null ? null : result.getJSONObject("data");
    }

    /**
     * @Author: 星禾
     * @Description: 获取指定虚拟机当前最大磁盘容量
     * @DateTime: 2026/6/8 12:25
     */
    public Long getVmMaxDisk(Master node, HashMap<String,String> cookie, Integer vmid) throws UnauthorizedException {
        JSONObject result = getVmStatus(node, cookie, vmid);
        if (result == null || result.getJSONObject("data") == null) {
            return null;
        }
        return result.getJSONObject("data").getLong("maxdisk");
    }

    /**
     * @Author: 星禾
     * @Description: 同步PVE系统盘显示容量，避免前端显示旧大小
     * @DateTime: 2026/6/8 12:25
     */
    public boolean syncVmSystemDiskDisplaySize(Master node, HashMap<String,String> cookie, Integer vmid, String targetSize)
            throws UnauthorizedException {
        JSONObject vmConfig = getVmConfig(node, cookie, vmid);
        if (vmConfig == null) {
            return false;
        }
        String scsi0 = vmConfig.getString("scsi0");
        if (scsi0 == null || scsi0.trim().isEmpty()) {
            return false;
        }
        String targetDiskConfig = VmUtil.upsertDiskOption(scsi0, "size", targetSize);
        if (!targetDiskConfig.equals(scsi0)) {
            resetVmConfig(node, cookie, vmid, "scsi0", targetDiskConfig);
        }
        JSONObject refreshedVmConfig = getVmConfig(node, cookie, vmid);
        String refreshedScsi0 = refreshedVmConfig == null ? null : refreshedVmConfig.getString("scsi0");
        return refreshedScsi0 != null && refreshedScsi0.contains("size=" + targetSize);
    }

    /**
     * @Author: 星禾
     * @Description: 启用集群和节点防火墙并保持默认策略放行
     * @DateTime: 2026/6/7 14:07
     */
    public void ensureFirewallEnabledAccept(Master node, HashMap<String,String> cookie) throws UnauthorizedException {
        enableClusterFirewallAccept(node, cookie);
        enableNodeFirewallAccept(node, cookie);
        startPveFirewallService(node, cookie);
    }

    /**
     * @Author: 星禾
     * @Description: 启用集群防火墙并保持输入输出默认ACCEPT
     * @DateTime: 2026/6/7 14:07
     */
    public void enableClusterFirewallAccept(Master node, HashMap<String,String> cookie) throws UnauthorizedException {
        HashMap<String,Object> params = new HashMap<>();
        params.put("enable", 1);
        params.put("policy_in", "ACCEPT");
        params.put("policy_out", "ACCEPT");
        putNodeApi(node, cookie, "/cluster/firewall/options", params);
    }

    /**
     * @Author: 星禾
     * @Description: 启用节点防火墙并尽量保持节点输入输出默认ACCEPT
     * @DateTime: 2026/6/7 14:07
     */
    public void enableNodeFirewallAccept(Master node, HashMap<String,String> cookie) throws UnauthorizedException {
        HashMap<String,Object> params = new HashMap<>();
        params.put("enable", 1);
        params.put("policy_in", "ACCEPT");
        params.put("policy_out", "ACCEPT");
        try {
            putNodeApi(node, cookie, "/nodes/" + node.getNodeName() + "/firewall/options", params);
        } catch (RestClientResponseException e) {
            if (!containsResponseBody(e, "policy_in") && !containsResponseBody(e, "policy_out")
                    && !containsResponseBody(e, "Parameter verification failed")) {
                throw e;
            }
            HashMap<String,Object> fallbackParams = new HashMap<>();
            fallbackParams.put("enable", 1);
            putNodeApi(node, cookie, "/nodes/" + node.getNodeName() + "/firewall/options", fallbackParams);
        }
    }

    /**
     * @Author: 星禾
     * @Description: 启动节点pve-firewall服务
     * @DateTime: 2026/6/7 14:07
     */
    public void startPveFirewallService(Master node, HashMap<String,String> cookie) throws UnauthorizedException {
        JSONObject result = getNodeApi(node, cookie, "/nodes/" + node.getNodeName() + "/services/pve-firewall/state", new HashMap<>());
        JSONObject data = result == null ? null : result.getJSONObject("data");
        String state = data == null ? null : data.getString("state");
        if ("running".equalsIgnoreCase(state)) {
            return;
        }
        postNodeApi(node, cookie, "/nodes/" + node.getNodeName() + "/services/pve-firewall/start", new HashMap<>());
    }

    /**
     * @Author: 星禾
     * @Description: 启用虚拟机级防IP和MAC伪造配置
     * @DateTime: 2026/6/7 22:47
     */
    public void enableVmFirewallAntiSpoof(Master node, HashMap<String,String> cookie, Integer vmid) throws UnauthorizedException {
        HashMap<String,Object> params = new HashMap<>();
        params.put("enable", 1);
        params.put("policy_in", "ACCEPT");
        params.put("policy_out", "ACCEPT");
        params.put("macfilter", 1);
        params.put("ipfilter", 1);
        params.put("dhcp", 1);
        params.put("ndp", 1);
        putNodeApi(node, cookie, "/nodes/" + node.getNodeName() + "/qemu/" + vmid + "/firewall/options", params);
    }

    /**
     * @Author: 星禾
     * @Description: 关闭虚拟机级防IP和MAC伪造配置
     * @DateTime: 2026/6/8 0:16
     */
    public void disableVmFirewallAntiSpoof(Master node, HashMap<String,String> cookie, Integer vmid) throws UnauthorizedException {
        HashMap<String,Object> params = new HashMap<>();
        params.put("enable", 0);
        params.put("policy_in", "ACCEPT");
        params.put("policy_out", "ACCEPT");
        params.put("macfilter", 0);
        params.put("ipfilter", 0);
        putNodeApi(node, cookie, "/nodes/" + node.getNodeName() + "/qemu/" + vmid + "/firewall/options", params);
    }

    /**
     * @Author: 星禾
     * @Description: 获取虚拟机防火墙IPSet条目
     * @DateTime: 2026/6/7 22:47
     */
    public JSONObject getVmFirewallIpsetEntries(Master node, HashMap<String,String> cookie, Integer vmid,
                                                String ipsetName) throws UnauthorizedException {
        try {
            return getNodeApi(node, cookie, "/nodes/" + node.getNodeName() + "/qemu/" + vmid
                    + "/firewall/ipset/" + encodePath(ipsetName), new HashMap<>());
        } catch (RestClientResponseException e) {
            if (isNotFound(e)) {
                return null;
            }
            throw e;
        }
    }

    /**
     * @Author: 星禾
     * @Description: 创建虚拟机防火墙IPSet
     * @DateTime: 2026/6/7 22:47
     */
    public void createVmFirewallIpset(Master node, HashMap<String,String> cookie, Integer vmid,
                                      String ipsetName) throws UnauthorizedException {
        HashMap<String,Object> params = new HashMap<>();
        params.put("name", ipsetName);
        params.put("comment", "QimenIDC auto managed VM IP allow list");
        try {
            postNodeApi(node, cookie, "/nodes/" + node.getNodeName() + "/qemu/" + vmid + "/firewall/ipset", params);
        } catch (RestClientResponseException e) {
            if (!isAlreadyExists(e)) {
                throw e;
            }
        }
    }

    /**
     * @Author: 星禾
     * @Description: 添加虚拟机防火墙IPSet条目
     * @DateTime: 2026/6/7 22:47
     */
    public void addVmFirewallIpsetEntry(Master node, HashMap<String,String> cookie, Integer vmid,
                                        String ipsetName, String cidr) throws UnauthorizedException {
        HashMap<String,Object> params = new HashMap<>();
        params.put("cidr", cidr);
        try {
            postNodeApi(node, cookie, "/nodes/" + node.getNodeName() + "/qemu/" + vmid
                    + "/firewall/ipset/" + encodePath(ipsetName), params);
        } catch (RestClientResponseException e) {
            if (!isAlreadyExists(e)) {
                throw e;
            }
        }
    }

    /**
     * @Author: 星禾
     * @Description: 删除虚拟机防火墙IPSet条目
     * @DateTime: 2026/6/7 22:47
     */
    public void deleteVmFirewallIpsetEntry(Master node, HashMap<String,String> cookie, Integer vmid,
                                           String ipsetName, String cidr) throws UnauthorizedException {
        try {
            deleteNodeApiByUri(node, cookie, "/nodes/" + encodePath(node.getNodeName()) + "/qemu/" + vmid
                    + "/firewall/ipset/" + encodePath(ipsetName) + "/" + encodePath(cidr));
        } catch (RestClientResponseException e) {
            if (!isNotFound(e)) {
                throw e;
            }
        }
    }

    /**
     * @Author: 星禾
     * @Description: 删除虚拟机防火墙IPSet
     * @DateTime: 2026/6/8 0:16
     */
    public void deleteVmFirewallIpset(Master node, HashMap<String,String> cookie, Integer vmid,
                                      String ipsetName) throws UnauthorizedException {
        try {
            deleteNodeApiByUri(node, cookie, "/nodes/" + encodePath(node.getNodeName()) + "/qemu/" + vmid
                    + "/firewall/ipset/" + encodePath(ipsetName));
        } catch (RestClientResponseException e) {
            if (!isNotFound(e)) {
                throw e;
            }
        }
    }
    /**
     * @Author: 星禾
     * @Description: 创建指定虚拟机快照
     * @DateTime: 2026/5/24 20:20
     * @Params: Master node 节点信息 HashMap<String,String> cookie 登录信息 Integer vmid 虚拟机ID String snapName 快照名称 Boolean vmstate是否保存运行内存状态
     * @Return none
     */
    public void addVmSnapShot(Master node, HashMap<String,String> cookie, Integer vmid, String snapName , Boolean vmstate,
    String description) throws UnauthorizedException {
        HashMap<String,Object> params = new HashMap<>();
        // snapname快照名称 vmstate是否保存运行内存状态，建议false description描述
        params.put("snapname", snapName);
        params.put("vmstate", vmstate);
        params.put("description", description);
        postNodeApi(node,cookie,"/nodes/" + node.getNodeName() + "/qemu/" + vmid + "/snapshot",params);
    }
    /**
     * @Author: 星禾
     * @Description: 删除指定虚拟机快照
     * @DateTime: 2026/5/24 20:21
     * @Params: Master node 节点信息 HashMap<String,String> cookie 登录信息 Integer vmid 虚拟机ID String snapName 快照名称
     * @Return none
     */
    public void deleteVmSnapShot(Master node, HashMap<String,String> cookie, Integer vmid, String snapName) throws UnauthorizedException {
        deleteNodeApi(node,cookie,"/nodes/" + node.getNodeName() + "/qemu/" + vmid + "/snapshot/" + snapName, new HashMap<>());
    }
    /**
     * @Author: 星禾
     * @Description: 回滚指定虚拟机的指定快照
     * @DateTime: 2026/5/24 20:22
     * @Params: Master node 节点信息 HashMap<String,String> cookie 登录信息 Integer vmid 虚拟机ID String snapName 快照名称
     * @Return none
     */
    public void rollbackVmSnapShot(Master node, HashMap<String,String> cookie, Integer vmid, String snapName) throws UnauthorizedException {
        postNodeApi(node,cookie,"/nodes/" + node.getNodeName() + "/qemu/" + vmid + "/snapshot/" + snapName + "/rollback", new HashMap<>());
    }
    /**
     * @Author: 星禾
     * @Description: 获取指定虚拟机备份
     * @DateTime: 2026/5/24 17:52
     * @Params: Master node 节点信息 HashMap<String,String> cookie 登录信息 Integer vmid 虚拟机ID
     * @Return none
     */
    public JSONObject getVmBackup(Master node, HashMap<String,String> cookie, Integer vmid) throws UnauthorizedException {
        return getVmBackup(node, cookie, vmid, null);
    }

    /**
     * @Author: 鏄熺
     * @Description: 获取指定虚拟机备份列表
     * @DateTime: 2026/5/29 23:03
     * @Params: Master node 节点信息 HashMap<String,String> cookie 登录信息 Integer vmid 虚拟机ID String storage 备份存储
     * @Return JSONObject
     */
    public JSONObject getVmBackup(Master node, HashMap<String,String> cookie, Integer vmid, String storage) throws UnauthorizedException {
        if (hasText(storage)) {
            return getNodeApi(node, cookie, "/nodes/" + node.getNodeName() + "/storage/" + encodePath(storage) + "/content?content=backup&vmid=" + vmid, new HashMap<>());
        }

        JSONObject storageJson = getNodeApi(node, cookie, "/nodes/" + node.getNodeName() + "/storage", new HashMap<>());
        JSONArray backupList = new JSONArray();
        if (storageJson == null) {
            JSONObject result = new JSONObject();
            result.put("data", backupList);
            return result;
        }

        JSONArray storageList = storageJson.getJSONArray("data");
        if (storageList != null) {
            for (int i = 0; i < storageList.size(); i++) {
                JSONObject storageItem = storageList.getJSONObject(i);
                if (storageItem == null || !hasBackupContent(storageItem)) {
                    continue;
                }

                String storageName = storageItem.getString("storage");
                JSONObject backups = getVmBackup(node, cookie, vmid, storageName);
                if (backups == null || backups.getJSONArray("data") == null) {
                    continue;
                }
                JSONArray data = backups.getJSONArray("data");
                for (int j = 0; j < data.size(); j++) {
                    JSONObject backup = data.getJSONObject(j);
                    if (backup != null) {
                        backup.put("storage", storageName);
                        backupList.add(backup);
                    }
                }
            }
        }

        JSONObject result = new JSONObject();
        result.put("data", backupList);
        return result;
    }

    /**
     * @Author: 鏄熺
     * @Description: 创建指定虚拟机备份
     * @DateTime: 2026/5/29 23:03
     * @Params: Master node 节点信息 HashMap<String,String> cookie 登录信息 Integer vmid 虚拟机ID String storage 备份存储 String mode 备份模式 String compress 压缩方式 String notes 备注
     * @Return JSONObject
     */
    public JSONObject addVmBackup(Master node, HashMap<String,String> cookie, Integer vmid, String storage, String mode,
                                  String compress, String notes) throws UnauthorizedException {
        HashMap<String,Object> params = new HashMap<>();
        params.put("vmid", vmid);
        params.put("storage", storage);
        params.put("mode", hasText(mode) ? mode : "snapshot");
        params.put("compress", hasText(compress) ? compress : "zstd");
        if (hasText(notes)) {
            params.put("notes-template", notes);
        }
        return postNodeApi(node, cookie, "/nodes/" + node.getNodeName() + "/vzdump", params);
    }

    /**
     * @Author: 鏄熺
     * @Description: 删除指定虚拟机备份
     * @DateTime: 2026/5/29 23:03
     * @Params: Master node 节点信息 HashMap<String,String> cookie 登录信息 String volid 备份卷标
     * @Return JSONObject
     */
    public JSONObject deleteVmBackup(Master node, HashMap<String,String> cookie, String volid) throws UnauthorizedException {
        String storage = getStorageFromVolid(volid);
        return deleteNodeApiByUri(node, cookie, "/nodes/" + encodePath(node.getNodeName()) + "/storage/" + encodePath(storage) + "/content/" + encodePath(volid));
    }

    /**
     * @Author: 鏄熺
     * @Description: 还原指定虚拟机备份
     * @DateTime: 2026/5/29 23:03
     * @Params: Master node 节点信息 HashMap<String,String> cookie 登录信息 Integer vmid 虚拟机ID String volid 备份卷标 Boolean force 是否覆盖 Boolean start 是否启动
     * @Return JSONObject
     */
    public JSONObject rollbackVmBackup(Master node, HashMap<String,String> cookie, Integer vmid, String volid,
                                       Boolean force, Boolean start) throws UnauthorizedException {
        validateBackupVolid(volid);
        HashMap<String,Object> params = new HashMap<>();
        params.put("vmid", vmid);
        params.put("archive", volid);
        if (force != null) {
            params.put("force", force ? 1 : 0);
        }
        if (start != null) {
            params.put("start", start ? 1 : 0);
        }
        return postNodeApi(node, cookie, "/nodes/" + node.getNodeName() + "/qemu", params);
    }

    private boolean hasBackupContent(JSONObject storageItem) {
        String content = storageItem.getString("content");
        if (!hasText(storageItem.getString("storage")) || content == null) {
            return false;
        }
        for (String item : content.split(",")) {
            if ("backup".equals(item.trim())) {
                return true;
            }
        }
        return false;
    }

    private String getStorageFromVolid(String volid) {
        validateBackupVolid(volid);
        return volid.substring(0, volid.indexOf(':'));
    }

    private void validateBackupVolid(String volid) {
        if (!hasText(volid) || !volid.contains(":") || !volid.contains("/")) {
            throw new IllegalArgumentException("备份卷标无效");
        }
    }

    private boolean isNotFound(RestClientResponseException e) {
        return e.getRawStatusCode() == HttpStatus.NOT_FOUND.value()
                || containsResponseBody(e, "not found");
    }

    private boolean isAlreadyExists(RestClientResponseException e) {
        return e.getRawStatusCode() == HttpStatus.CONFLICT.value()
                || containsResponseBody(e, "already exists")
                || containsResponseBody(e, "file exists")
                || containsResponseBody(e, "exists");
    }

    private boolean containsResponseBody(RestClientResponseException e, String keyword) {
        if (e == null || keyword == null) {
            return false;
        }
        String body = e.getResponseBodyAsString();
        return body != null && body.toLowerCase().contains(keyword.toLowerCase());
    }

    private String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
