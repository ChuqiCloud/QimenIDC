package com.chuqiyun.proxmoxveams.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.dto.SecurityGroupApplyDto;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.service.MasterService;
import io.swagger.models.auth.In;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import com.chuqiyun.proxmoxveams.config.RestTemplateConfig;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Resource;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * @author mryunqi
 * @date 2023/7/8
 */
@Component
public class ClientApiUtil {
    private static final Logger log = LoggerFactory.getLogger(ClientApiUtil.class);

    /**
     * 被控端协议，被控端只监听回环端口，由 nginx 终止 TLS 并提供管理源地址白名单
     */
    private static final String AGENT_SCHEME = "https://";

    /**
     * 默认读超时，与历史实现保持一致
     */
    private static final int DEFAULT_TIMEOUT_MILLIS = 30_000;

    /**
     * 迁移阶段接口是同步执行后返回，vzdump/qmrestore 可能跑很久，单独放宽超时
     */
    private static final int MIGRATION_ACTION_TIMEOUT_MILLIS = 24 * 60 * 60 * 1000;

    /**
     * 单次拉取的迁移日志上限，避免日志过大撑爆响应
     */
    private static final int MIGRATION_LOG_CHUNK_BYTES = 64 * 1024;

    /**
     * 主控版本，随请求头下发给被控端，便于两侧对齐版本排查问题
     */
    private static String masterVersion = "";

    @Value("${info.application.version:}")
    public void setMasterVersion(String version){
        ClientApiUtil.masterVersion = version;
    }

    // get请求https://gitee.com/chuqicloud/soft-ware/raw/master/Cloud/os.json
    /**
    * @Author: mryunqi
    * @Description: 获取系统镜像下载地址json
    * @DateTime: 2023/7/8 18:12
    * @Params:
    * @Return
    */
    public static JSONObject getNetOs(String url){
        RestTemplate restTemplate = RestTemplateConfig.createRestTemplate();
        //请求url获取内容
        String json = restTemplate.getForObject(url, String.class);
        //将json转换为JSONObject对象
        return JSONObject.parseObject(json);
    }

    /**
    * @Author: 星禾
    * @Description: 通用GET请求（请求参数随URL明文传输，响应体按协商结果解密）
    */
    public static JSONObject getControllerApi(String url, Map<String, Object> params, String token){
        return request("GET", url, params, token, false, DEFAULT_TIMEOUT_MILLIS);
    }

    public static JSONObject getControllerApi(String url, Map<String, Object> params, String token, int timeoutMillis){
        return request("GET", url, params, token, false, timeoutMillis);
    }

    /**
    * @Author: 星禾
    * @Description: 通用POST请求（请求体加密，响应体解密）
    */
    public static JSONObject postControllerApi(String url, Map<String, Object> params, String token){
        return request("POST", url, params, token, true, DEFAULT_TIMEOUT_MILLIS);
    }

    public static JSONObject postControllerApi(String url, Map<String, Object> params, String token, int timeoutMillis){
        return request("POST", url, params, token, true, timeoutMillis);
    }

    /**
    * @Author: 星禾
    * @Description: 被控端请求统一入口
     * 业务参数只允许在提供方声明的字段集合内传递，额外字段直接丢弃，
     * 目标分支已将路径类接口下线，所有请求走TLS通道并由nginx做管理源地址白名单。
    * @Params: method 请求方法
     * @Params: rawUrl 被控端接口地址
     * @Params: params 业务参数
     * @Params: token 被控端token
     * @Params: encryptBody 是否加密请求体
    * @Return JSONObject 被控端响应，失败返回null
    */
    private static JSONObject request(String method, String rawUrl, Map<String, Object> params, String token,
                                     boolean encryptBody, int timeoutMillis){
        if (ClientPayloadUtil.isFieldWhitelistEnabled()) {
            filterJsonFields(params, rawUrl);
        }
        String path = URI.create(rawUrl).getRawPath();
        // 防重放参数放请求头，避免落到被控端与中间设备的访问日志里
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String nonce = UUID.randomUUID().toString().replace("-", "");
        UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromHttpUrl(rawUrl);
        RestTemplate restTemplate = RestTemplateConfig.createQAgentRestTemplate(timeoutMillis);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, token);
        headers.add("X-QimenIDC-Version", getMasterVersion());
        headers.add("X-QimenIDC-Timestamp", timestamp);
        headers.add("X-QimenIDC-Nonce", nonce);
        try {
            ResponseEntity<String> response;
            if ("GET".equals(method)) {
                // 模板变量的值必须与占位的 key 同名，否则 exchange 的 varargs 无法解析
                Map<String, Object> uriVariables = new HashMap<>();
                if (params != null && !params.isEmpty()) {
                    for (Map.Entry<String, Object> entry : params.entrySet()) {
                        urlBuilder.queryParam(entry.getKey(), "{" + entry.getKey() + "}");
                        uriVariables.put(entry.getKey(), entry.getValue());
                    }
                }
                HttpEntity<Object> entity = new HttpEntity<>(null, headers);
                response = restTemplate.exchange(urlBuilder.build(false).encode().toUriString(),
                        HttpMethod.GET, entity, String.class, uriVariables);
            } else {
                String body;
                if (encryptBody && ClientPayloadUtil.isEncryptEnabled()) {
                    body = ClientPayloadUtil.encrypt(token, JSON.toJSONString(params == null ? new HashMap<>() : params),
                            method, path, timestamp, nonce);
                    headers.add("Content-Type", "text/plain; charset=UTF-8");
                } else {
                    body = ClientPayloadUtil.buildPlainPayload(new JSONObject(params == null ? new HashMap<>() : params));
                    headers.add("Content-Type", "application/json");
                }
                HttpEntity<String> entity = new HttpEntity<>(body, headers);
                response = restTemplate.exchange(urlBuilder.build(false).encode().toUriString(),
                        HttpMethod.POST, entity, String.class);
            }
            return parseResponse(response.getBody(), token, method, path, timestamp, nonce);
        } catch (RestClientException e) {
            log.error("调用被控端接口失败: url={}, error={}", rawUrl, e.getMessage());
            return null;
        }
    }

    /**
    * @Author: 星禾
    * @Description: 解析被控端响应，密文负载解密后再解析
    * @Return JSONObject 响应体，解密失败返回null
    */
    private static JSONObject parseResponse(String body, String token, String method, String path, String timestamp, String nonce){
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        JSONObject response = JSON.parseObject(body);
        if (response == null || !response.containsKey("_encrypted")) {
            return response;
        }
        String plain = ClientPayloadUtil.decrypt(token, response.getString("_encrypted"), method, path, timestamp, nonce);
        if (plain == null) {
            log.error("被控端响应解密失败，可能存在中间人篡改: path={}", path);
            return null;
        }
        return JSON.parseObject(plain);
    }

    /**
    * @Author: 星禾
    * @Description: 丢弃被控端未声明的参数，避免伪造字段被下发给节点
    */
    private static void filterJsonFields(Map<String, Object> params, String rawUrl){
        if (params == null || params.isEmpty()) {
            return;
        }
        Set<String> allowed = ControllerApiSchema.getAllowedFields(rawUrl);
        if (allowed == null || allowed.isEmpty()) {
            return;
        }
        List<String> unknown = new ArrayList<>();
        for (String key : params.keySet()) {
            if (!allowed.contains(key)) {
                unknown.add(key);
            }
        }
        if (!unknown.isEmpty()) {
            log.warn("[SECURITY] 被控端请求 {} 携带未声明参数，已忽略: {}", rawUrl, String.join(",", unknown));
            for (String key : unknown) {
                params.remove(key);
            }
        }
    }

    /**
    * @Author: 星禾
    * @Description: 读取被控端版本文件，用于判定被控端是否具备新接口能力（免鉴权接口，仅返回固定值）
    * @Return String 被控端版本，不可用时返回null
    */
    public static String getAgentVersion(String ip, Integer port, String token){
        String url = AGENT_SCHEME + ip + ":" + port + "/version";
        RestTemplate restTemplate = RestTemplateConfig.createQAgentRestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, token);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            JSONObject body = JSON.parseObject(response.getBody());
            JSONObject data = body == null ? null : body.getJSONObject("data");
            return data == null ? null : data.getString("version");
        } catch (RestClientException e) {
            log.error("获取被控端版本失败: ip={}, port={}, error={}", ip, port, e.getMessage());
            return null;
        }
    }

    /**
    * @Author: 星禾
    * @Description: 下发管理源白名单，被控端把入参与自身实测来源取并集后落盘并重载 nginx
    * @Params: networks 集群内主控出口地址
    * @Params: clusterId 集群标识，用于区分不同集群的管理源
    * @Return JSONObject 被控端同步结果，失败返回null
    */
    public static JSONObject syncTrustedNetworks(String ip, Integer port, String token,
                                                 List<String> networks, String clusterId, int timeoutMillis){
        String url = AGENT_SCHEME + ip + ":" + port + "/net/trust";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("networks", networks == null ? new ArrayList<>() : networks);
        paramMap.put("cluster_id", clusterId == null ? "" : clusterId);
        return postControllerApi(url, paramMap, token, timeoutMillis);
    }

    /**
    * @Author: 星禾
    * @Description: 主控版本，随请求头下发给被控做一致性校验
    */
    private static String getMasterVersion(){
        return masterVersion;
    }

    /**
    * @Author: mryunqi
    * @Description: 获取被控端连接状态
    * @DateTime: 2023/7/11 17:45
    */
    public static JSONObject getControllerConnectStatus(String ip, Integer port,String token){
        String url = AGENT_SCHEME + ip + ":" + port + "/status";
        RestTemplate restTemplate = RestTemplateConfig.createQAgentRestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, token);
        HttpEntity<Object> entity = new HttpEntity<>(headers);
        ResponseEntity<String> result = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        return parseResponse(result.getBody(), token, "GET", "/status", null, null);
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
        return null;
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
        String url = AGENT_SCHEME + ip + ":" + port + "/deleteFile";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("path","/home/images/");
        paramMap.put("file",osName);
        JSONObject result = postControllerApi(url, paramMap, token);
        return result != null && result.getInteger("code") == 200;
    }

    /**
    * @Author: 星禾
    * @Description: 获取节点指定目录下的文件列表
    * @Params: ip 被控端ip, port 端口, token 令牌, path 目录
    * @Return JSONArray 文件名列表，失败返回null
    */
    public static JSONArray getPathFileList(String ip, Integer port, String token, String path){
        String url = AGENT_SCHEME + ip + ":" + port + "/pathFile";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("path", path);
        // 走加密POST，路径不落到URL与访问日志里
        JSONObject result = postControllerApi(url, paramMap, token);
        if (result == null || result.getInteger("code") != 200){
            return null;
        }
        JSONObject data = result.getJSONObject("data");
        return data == null ? null : data.getJSONArray("files");
    }

    /**
    * @Author: 星禾
    * @Description: 通过被控端写入文本文件，目录不存在时由被控端创建
    * @DateTime: 2026/7/18 10:00
    * @Params: ip 被控端ip
     * @Params: token 被控端token
     * @Params: controllerPort 被控端端口
     * @Params: path 文件目录
     * @Params: filename 文件名
     * @Params: content 文件内容
    * @Return Boolean 是否写入成功
    */
    public static Boolean writeFile(String ip, Integer controllerPort, String token, String path, String filename, String content){
        String url = AGENT_SCHEME + ip + ":" + controllerPort + "/writeFile";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("path",path);
        paramMap.put("filename",filename);
        paramMap.put("content",content);
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
        String url = AGENT_SCHEME + ip + ":" + port + "/changePassword";
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
        String url = AGENT_SCHEME + ip + ":" + port + "/importDisk";
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
        String url = AGENT_SCHEME + ip + ":" + port + "/readFile";
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
        String url = AGENT_SCHEME + ip + ":" + controllerPort + "/vnc";
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
        String url = AGENT_SCHEME + ip + ":" + controllerPort + "/vnc/import";
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
    * @Author: 星禾
    * @Description: 确保指定VNC服务存在，不存在时由被控端重新拉起
    * @DateTime: 2026/7/16 21:45
    */
    public static Boolean ensureVncService(String ip, String token, Integer vmid, Integer vncPort,String username, String vncPassword,Integer controllerPort, String vncHost, Integer time){
        String url = AGENT_SCHEME + ip + ":" + controllerPort + "/vnc/ensure";
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
        String url = AGENT_SCHEME + ip + ":" + controllerPort + "/vnc/stop";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("vnc_file_path","/home/software/vnc");
        paramMap.put("vmid","1");//被控写了不能传空置，随机填个数
        paramMap.put("username","1");
        paramMap.put("password","1");
        paramMap.put("port",vncPort);
        paramMap.put("host","1");
        paramMap.put("time","1");
        JSONObject result = postControllerApi(url, paramMap, token);
        return result != null && result.getInteger("code") == 200;
    }

    /**
     * 获取被控节点上虚拟机磁盘的实际分配容量。
     */
    public static JSONObject getVmDiskUsage(String ip, String token, Integer controllerPort, JSONObject request) {
        String url = AGENT_SCHEME + ip + ":" + controllerPort + "/disk/usage";
        Map<String, Object> params = new HashMap<>(request);
        JSONObject result = postControllerApi(url, params, token);
        if (result == null || result.getInteger("code") != 200) {
            return null;
        }
        return result.getJSONObject("data");
    }

    public static JSONObject startMigrationBackup(String ip, String token, Integer controllerPort, String taskId, Integer vmid, String backupDir) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("task_id", taskId);
        paramMap.put("action", "backup");
        paramMap.put("vmid", vmid);
        if (backupDir != null && !backupDir.trim().isEmpty()) {
            paramMap.put("backup_dir", backupDir);
        }
        return postMigrationAction(ip, token, controllerPort, paramMap);
    }

    public static JSONObject startMigrationRestore(String ip, String token, Integer controllerPort, String taskId, String sourceUrl,
                                                   String sourceToken, Integer targetVmid, String targetStorage, String backupDir) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("task_id", taskId);
        paramMap.put("action", "restore");
        paramMap.put("source_url", sourceUrl);
        paramMap.put("source_token", sourceToken);
        paramMap.put("target_vmid", targetVmid);
        paramMap.put("target_storage", targetStorage);
        if (backupDir != null && !backupDir.trim().isEmpty()) {
            paramMap.put("backup_dir", backupDir);
        }
        return postMigrationAction(ip, token, controllerPort, paramMap);
    }

    /**
     * 同步执行被控端单个迁移阶段（backup / restore），失败可直接用同一 taskId 重试。
     * 被控端会在阶段完成后返回，因此超时时间按最长迁移场景放宽。
     */
    private static JSONObject postMigrationAction(String ip, String token, Integer controllerPort, Map<String, Object> paramMap) {
        String url = AGENT_SCHEME + ip + ":" + controllerPort + "/migrationAction/run";
        return postControllerApi(url, paramMap, token, MIGRATION_ACTION_TIMEOUT_MILLIS);
    }

    public static JSONObject getMigrationStatus(String ip, String token, Integer controllerPort, String taskId) {
        String url = AGENT_SCHEME + ip + ":" + controllerPort + "/migrationAction/status/" + taskId;
        Map<String, Object> paramMap = new HashMap<>();
        return getControllerApi(url, paramMap, token);
    }

    /**
     * 增量拉取被控端迁移日志，offset 为上次返回的 nextOffset。
     */
    public static JSONObject getMigrationLog(String ip, String token, Integer controllerPort, String taskId, long offset) {
        String url = AGENT_SCHEME + ip + ":" + controllerPort + "/migrationAction/log/" + taskId
                + "?offset=" + offset + "&limit=" + MIGRATION_LOG_CHUNK_BYTES;
        Map<String, Object> paramMap = new HashMap<>();
        return getControllerApi(url, paramMap, token);
    }

    public static JSONObject cleanupMigrationSource(String ip, String token, Integer controllerPort, String taskId, Integer vmid) {
        String url = AGENT_SCHEME + ip + ":" + controllerPort + "/migrationAction/cleanup-source";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("task_id", taskId);
        paramMap.put("vmid", vmid);
        paramMap.put("purge", true);
        return postControllerApi(url, paramMap, token);
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
        String url = AGENT_SCHEME + ip + ":" + controllerPort + "/restartNetwork";
        Map<String, Object> paramMap = new HashMap<>();
        JSONObject result = postControllerApi(url, paramMap, token);
        return result != null && result.getInteger("code") == 200;
    }

    public static JSONObject exportNatRules(String ip, String token, Integer controllerPort) {
        String url = AGENT_SCHEME + ip + ":" + controllerPort + "/nat/export";
        JSONObject result = getControllerApi(url, new HashMap<>(), token);
        return result != null && result.getInteger("code") == 200 ? result : null;
    }

    public static Boolean syncNatRules(String ip, String token, Integer controllerPort,
                                       JSONArray portRules, JSONArray ipForwardRules) {
        String url = AGENT_SCHEME + ip + ":" + controllerPort + "/nat/sync";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("port_rules", portRules);
        paramMap.put("ip_forward_rules", ipForwardRules);
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
        return addPortForward(ip, token, controllerPort, hostId, ip, source_port, destination_ip, destination_port, protocol);
    }

    public static Boolean addPortForward(String ip, String token, Integer controllerPort, Integer hostId,
                                         String sourceIp, Integer source_port, String destination_ip,
                                         Integer destination_port, String protocol){
        String url = AGENT_SCHEME + ip + ":" + controllerPort + "/nat/add";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("vm",hostId);
        paramMap.put("source_ip",sourceIp);
        paramMap.put("source_port",source_port);
        paramMap.put("destination_ip",destination_ip);
        paramMap.put("destination_port",destination_port);
        paramMap.put("protocol",protocol);
        if (Objects.equals(protocol, "all")) //处理tcp+udp请求
        {
            paramMap.put("protocol","tcp");
            JSONObject result = postControllerApi(url, paramMap, token);
            if ( result != null && result.getInteger("code") == 200 ) {
                paramMap.put("protocol","udp");
                JSONObject result_udp = postControllerApi(url, paramMap, token);
                if (result_udp != null && result_udp.getInteger("code") == 200) {
                    return true;
                }
                paramMap.put("protocol", "tcp");
                postControllerApi(AGENT_SCHEME + ip + ":" + controllerPort + "/nat/delete", paramMap, token);
                return false;
            } else {
                return result != null && result.getInteger("code") == 200;
            }
        } else {
            JSONObject result = postControllerApi(url, paramMap, token);
            return result != null && result.getInteger("code") == 200;
        }
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
        return deletePortForward(ip, token, controllerPort, hostId, ip, source_port, destination_ip, destination_port, protocol);
    }

    public static Boolean deletePortForward(String ip, String token, Integer controllerPort, Integer hostId,
                                            String sourceIp, Integer source_port, String destination_ip,
                                            Integer destination_port, String protocol) {
        String url = AGENT_SCHEME + ip + ":" + controllerPort + "/nat/delete";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("vm", hostId);
        paramMap.put("source_ip", sourceIp);
        paramMap.put("source_port", source_port);
        paramMap.put("destination_ip", destination_ip);
        paramMap.put("destination_port", destination_port);
        paramMap.put("protocol", protocol);
        if (Objects.equals(protocol, "all")) {
            paramMap.put("protocol", "tcp");
            JSONObject tcpResult = postControllerApi(url, paramMap, token);
            paramMap.put("protocol", "udp");
            JSONObject udpResult = postControllerApi(url, paramMap, token);
            return tcpResult != null && tcpResult.getInteger("code") == 200
                    && udpResult != null && udpResult.getInteger("code") == 200;
        }
        JSONObject result = postControllerApi(url, paramMap, token);
        return result != null && result.getInteger("code") == 200;
    }

    public static Boolean addIpForward(String ip, String token, Integer controllerPort, Integer hostId, String publicIp, String privateIp) {
        String url = AGENT_SCHEME + ip + ":" + controllerPort + "/nat/addIpForward";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("vm", hostId);
        paramMap.put("source_ip", publicIp);
        paramMap.put("destination_ip", privateIp);
        JSONObject result = null;
        try {
            result = postControllerApi(url, paramMap, token);
        } catch (RestClientException e) {
            System.out.println("[VPC-IP-FORWARD] add request failed url=" + url + ", publicIp=" + publicIp
                    + ", privateIp=" + privateIp + ", error=" + e.getMessage());
            return false;
        }
        if (result == null || result.getInteger("code") != 200) {
            System.out.println("[VPC-IP-FORWARD] add failed url=" + url + ", publicIp=" + publicIp + ", privateIp=" + privateIp + ", result=" + result);
        }
        return result != null && result.getInteger("code") == 200;
    }

    public static Boolean deleteIpForward(String ip, String token, Integer controllerPort, Integer hostId, String publicIp, String privateIp) {
        String url = AGENT_SCHEME + ip + ":" + controllerPort + "/nat/deleteIpForward";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("vm", hostId);
        paramMap.put("source_ip", publicIp);
        paramMap.put("destination_ip", privateIp);
        JSONObject result = null;
        try {
            result = postControllerApi(url, paramMap, token);
        } catch (RestClientException e) {
            System.out.println("[VPC-IP-FORWARD] delete request failed url=" + url + ", publicIp=" + publicIp
                    + ", privateIp=" + privateIp + ", error=" + e.getMessage());
            return false;
        }
        if (result == null || result.getInteger("code") != 200) {
            System.out.println("[VPC-IP-FORWARD] delete failed url=" + url + ", publicIp=" + publicIp + ", privateIp=" + privateIp + ", result=" + result);
        }
        return result != null && result.getInteger("code") == 200;
    }

    public static Boolean applySecurityGroup(String ip, String token, Integer controllerPort, SecurityGroupApplyDto applyDto) {
        JSONObject result = applySecurityGroupResult(ip, token, controllerPort, applyDto);
        return result != null && Objects.equals(result.getInteger("code"), 200);
    }

    public static JSONObject applySecurityGroupResult(String ip, String token, Integer controllerPort, SecurityGroupApplyDto applyDto) {
        String url = AGENT_SCHEME + ip + ":" + controllerPort + "/security-group/apply";
        JSONObject result;
        try {
            result = postControllerApi(url, JSONObject.parseObject(JSONObject.toJSONString(applyDto)), token);
        } catch (RestClientException e) {
            result = securityGroupFailure("安全组下发请求失败: " + e.getMessage());
        }
        if (result == null || !Objects.equals(result.getInteger("code"), 200)) {
            System.out.println("[SECURITY-GROUP] apply failed url=" + url + ", hostId="
                    + (applyDto == null ? null : applyDto.getHostId()) + ", result=" + result);
        }
        return result;
    }

    public static Boolean deleteSecurityGroup(String ip, String token, Integer controllerPort, Integer hostId) {
        JSONObject result = deleteSecurityGroupResult(ip, token, controllerPort, hostId);
        return result != null && Objects.equals(result.getInteger("code"), 200);
    }

    public static JSONObject deleteSecurityGroupResult(String ip, String token, Integer controllerPort, Integer hostId) {
        String url = AGENT_SCHEME + ip + ":" + controllerPort + "/security-group/delete";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("hostId", hostId);
        JSONObject result;
        try {
            result = postControllerApi(url, paramMap, token);
        } catch (RestClientException e) {
            result = securityGroupFailure("安全组删除请求失败: " + e.getMessage());
        }
        if (result == null || !Objects.equals(result.getInteger("code"), 200)) {
            System.out.println("[SECURITY-GROUP] delete failed url=" + url + ", hostId=" + hostId + ", result=" + result);
        }
        return result;
    }

    public static JSONObject checkSecurityGroup(String ip, String token, Integer controllerPort, Integer hostId) {
        JSONObject result = checkSecurityGroupResult(ip, token, controllerPort, hostId);
        return result != null && Objects.equals(result.getInteger("code"), 200)
                ? result.getJSONObject("data") : null;
    }

    public static JSONObject checkSecurityGroupResult(String ip, String token, Integer controllerPort, Integer hostId) {
        String url = AGENT_SCHEME + ip + ":" + controllerPort + "/security-group/check";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("hostId", hostId);
        try {
            JSONObject result = postControllerApi(url, paramMap, token);
            if (result == null || !Objects.equals(result.getInteger("code"), 200)) {
                System.out.println("[SECURITY-GROUP] check failed url=" + url + ", hostId=" + hostId + ", result=" + result);
            }
            return result;
        } catch (RestClientException e) {
            System.out.println("[SECURITY-GROUP] check request failed url=" + url + ", hostId=" + hostId
                    + ", error=" + e.getMessage());
            return securityGroupFailure("安全组查询请求失败: " + e.getMessage());
        }
    }

    private static JSONObject securityGroupFailure(String message) {
        JSONObject result = new JSONObject();
        result.put("code", 500);
        result.put("message", message);
        result.put("data", new JSONObject());
        return result;
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
        String url = AGENT_SCHEME + ip + ":" + controllerPort + "/nat/getVm/"+page+"/"+size;
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("vm",hostId);
        JSONObject result = getControllerApi(url, paramMap, token);
        return result != null && result.getInteger("code") == 200 ? result : null;
    }
    /**
     * @Author: 星禾
     * @Description: 添加端口转发接口
     * @DateTime: 2025/1/22 15:41
     * @Params: nataddr 转发地址
     * @Params: bridge 转发虚拟网口
     */
    public static Boolean addNatBridge(String ip, String token, Integer controllerPort, String nataddr, String bridge){
        String url = AGENT_SCHEME + ip + ":" + controllerPort + "/nat/addBridge";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("nataddr",nataddr);
        paramMap.put("bridge",bridge);
        JSONObject result = postControllerApi(url, paramMap, token);
        return result != null && result.getInteger("code") == 200;
    }
}
