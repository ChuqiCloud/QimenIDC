package com.chuqiyun.proxmoxveams.controller.api.common;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.annotation.PublicApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.utils.QAgentVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;

/**
 * @author mryunqi
 * @date 2023/11/26
 */
@RestController
@RequestMapping("/api/common")
public class VersionPublicApi {
    private static final String NEW_VERSION_URL = "https://gitee.com/chuqicloud/soft-ware/raw/master/Cloud/version.json";
    private final RestTemplate restTemplate = createRestTemplate();

    @Value("${info.application.version}")
    private String version;
    /**
     * 内测版号
     */
    @Value("${info.application.build.version}")
    private String buildVersion;

    /**
     * 系统名称
     */
    @Value("${info.application.name}")
    private String name;

    /**
     * 系统描述
     */
    @Value("${info.application.description}")
    private String description;

    /**
    * @Author: mryunqi
    * @Description: 获取系统版本信息
    * @DateTime: 2023/11/26 22:17
    */
    @PublicApiCheck
    @RequestMapping("/version")
    public ResponseResult<Object> version() {
        HashMap<String, String> map = new HashMap<>();
        map.put("name", name);
        map.put("description", description);
        map.put("version", version);
        map.put("buildVersion", buildVersion);
        map.put("qagentVersion", QAgentVersion.getVersion());
        return ResponseResult.ok(map);
    }

    @PublicApiCheck
    @RequestMapping("/newversion")
    public ResponseResult<Object> newVersion() {
        String body = restTemplate.getForObject(NEW_VERSION_URL, String.class);
        JSONObject releases = JSON.parseObject(body);
        if (releases == null) {
            return ResponseResult.fail("无法读取发布版本信息");
        }
        JSONObject qagent = releases.getJSONObject("qagent");
        if (qagent == null) {
            qagent = new JSONObject();
            releases.put("qagent", qagent);
        }
        String qagentVersion = qagent.getString("version");
        if (qagentVersion == null || qagentVersion.trim().isEmpty()) {
            // 旧发布清单尚未维护被控版本时，明确标记这是主控随包版本。
            qagent.put("version", QAgentVersion.getVersion());
            qagent.put("source", "bundled");
        } else if (!qagent.containsKey("source")) {
            qagent.put("source", "release");
        }
        return ResponseResult.ok(releases);
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }
}
