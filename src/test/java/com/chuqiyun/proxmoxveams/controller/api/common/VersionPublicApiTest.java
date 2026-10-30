package com.chuqiyun.proxmoxveams.controller.api.common;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.service.ReleaseVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * @Author: 星禾
 * @Description: 验证 QAgent 最新版本仅来自统一线上发布清单，获取失败不影响主控版本返回
 * @DateTime: 2026/10/30 13:41
 */
class VersionPublicApiTest {
    private static final String RELEASE_URL = "https://gitee.com/chuqicloud/soft-ware/raw/master/Cloud/version.json";
    private final VersionPublicApi api = new VersionPublicApi();
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        ReleaseVersionService releases = new ReleaseVersionService();
        RestTemplate template = (RestTemplate) ReflectionTestUtils.getField(releases, "restTemplate");
        server = MockRestServiceServer.bindTo(template).build();
        ReflectionTestUtils.setField(api, "releaseVersionService", releases);
        ReflectionTestUtils.setField(api, "version", "1.3.5");
        ReflectionTestUtils.setField(api, "buildVersion", "1.3.5_3");
        ReflectionTestUtils.setField(api, "name", "QimenIDC");
        ReflectionTestUtils.setField(api, "description", "test");
    }

    @Test
    void includesOnlineQAgentVersionAlongsideManagerVersion() {
        reply("{\"qagent\":{\"version\":\"9.0.0\"}}");

        Map<?, ?> data = (Map<?, ?>) api.version().getData();

        assertThat(data.get("version")).isEqualTo("1.3.5");
        assertThat(data.get("buildVersion")).isEqualTo("1.3.5_3");
        assertThat(data.get("qagentVersion")).isEqualTo("9.0.0");
        server.verify();
    }

    @Test
    void preservesReleaseManifestWithoutInventingVersionOrSource() {
        String manifest = """
                {"frontend":{"version":"2.0-beta"},"backend":{"version":"2.0_1"},
                 "other":{"retained":true},
                 "qagent":{"version":"9.0.0","release_date":"2026-10-30","update_commands":"qa"}}
                """;
        reply(manifest);

        JSONObject data = (JSONObject) api.newVersion().getData();

        assertThat(data).isEqualTo(JSONObject.parseObject(manifest));
        assertThat(data.getJSONObject("qagent")).doesNotContainKey("source");
        server.verify();
    }

    @Test
    void oldManifestRemainsUnchangedAndDoesNotUseBundledQAgent() {
        reply("{\"frontend\":{\"version\":\"1\"},\"backend\":{\"version\":\"2\"}}");

        JSONObject data = (JSONObject) api.newVersion().getData();

        assertThat(data).doesNotContainKey("qagent");
        assertThat(data.getJSONObject("frontend").getString("version")).isEqualTo("1");
        assertThat(data.getJSONObject("backend").getString("version")).isEqualTo("2");
        server.verify();
    }

    @Test
    void missingLatestVersionStillReturnsManagerMetadata() {
        reply("{\"frontend\":{\"version\":\"1\"}}");

        Map<?, ?> data = (Map<?, ?>) api.version().getData();

        assertThat(data.containsKey("qagentVersion")).isTrue();
        assertThat(data.get("qagentVersion")).isNull();
        assertThat(data.get("name")).isEqualTo("QimenIDC");
        assertThat(data.get("buildVersion")).isEqualTo("1.3.5_3");
        server.verify();
    }

    @Test
    void unavailableManifestCannotMakeManagerVersionUnavailable() {
        server.expect(requestTo(RELEASE_URL)).andRespond(withServerError());

        Map<?, ?> data = (Map<?, ?>) api.version().getData();

        assertThat(data.get("qagentVersion")).isNull();
        assertThat(data.get("version")).isEqualTo("1.3.5");
        server.verify();
    }

    @Test
    void returnsFailureWhenOnlineReleaseManifestIsUnavailable() {
        server.expect(requestTo(RELEASE_URL)).andRespond(withServerError());

        assertThat(api.newVersion().getCode()).isEqualTo(20400);
        server.verify();
    }

    private void reply(String body) {
        server.expect(requestTo(RELEASE_URL))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}
