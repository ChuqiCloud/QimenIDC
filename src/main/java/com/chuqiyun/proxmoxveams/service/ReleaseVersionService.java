package com.chuqiyun.proxmoxveams.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.entity.Master;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;

/**
 * @Author: 星禾
 * @Description: 统一读取线上发布清单，最新 QAgent 版本不在主控保存或使用随包版本回退
 * @DateTime: 2026/10/30 13:55
 */
@Slf4j
@Service
public class ReleaseVersionService {
    private static final String NEW_VERSION_URL = "https://gitee.com/chuqicloud/soft-ware/raw/master/Cloud/version.json";
    private final RestTemplate restTemplate = createRestTemplate();

    public JSONObject getReleases() {
        try {
            String body = restTemplate.getForObject(NEW_VERSION_URL, String.class);
            return JSON.parseObject(body);
        } catch (RestClientException | JSONException e) {
            log.warn("读取线上发布版本失败: {}", e.getMessage());
            return null;
        }
    }

    public String getQagentVersion() {
        JSONObject releases = getReleases();
        if (releases == null || !(releases.get("qagent") instanceof JSONObject)) {
            return null;
        }
        JSONObject qagent = (JSONObject) releases.get("qagent");
        Object reported = qagent.get("version");
        if (!(reported instanceof String)) {
            return null;
        }
        String version = ((String) reported).trim();
        return version.isEmpty() ? null : version;
    }

    /** 同一页节点共用一次线上查询，序列化实体时不发起网络请求。 */
    public void fillQagentLatestVersion(Collection<Master> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        String version = getQagentVersion();
        for (Master node : nodes) {
            node.setQagentLatestVersion(version);
        }
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }
}
