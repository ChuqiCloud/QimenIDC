package com.chuqiyun.proxmoxveams.controller.api.common;

import com.alibaba.fastjson2.JSON;
import com.chuqiyun.proxmoxveams.annotation.PublicApiCheck;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import org.springframework.beans.factory.annotation.Value;
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
        return ResponseResult.ok(map);
    }

    @PublicApiCheck
    @RequestMapping("/newversion")
    public ResponseResult<Object> newVersion() {
        RestTemplate restTemplate = new RestTemplate();
        String body = restTemplate.getForObject(NEW_VERSION_URL, String.class);
        return ResponseResult.ok(JSON.parse(body));
    }
}
