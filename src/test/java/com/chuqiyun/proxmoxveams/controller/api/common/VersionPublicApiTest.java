package com.chuqiyun.proxmoxveams.controller.api.common;

import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.utils.QAgentVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * @Author: 星禾
 * @Description: 验证统一版本接口保留前后端发布清单，并准确区分 QAgent 远端发布和随包版本
 * @DateTime: 2026/10/30 13:41
 */
class VersionPublicApiTest {
    private final VersionPublicApi api = new VersionPublicApi();
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestTemplate template = (RestTemplate) ReflectionTestUtils.getField(api, "restTemplate");
        server = MockRestServiceServer.bindTo(template).build();
    }

    @Test
    void includesBundledQAgentVersionAlongsideManagerVersion() throws Exception {
        ReflectionTestUtils.setField(api, "version", "1.3.5");
        ReflectionTestUtils.setField(api, "buildVersion", "1.3.5_3");
        ReflectionTestUtils.setField(api, "name", "QimenIDC");
        ReflectionTestUtils.setField(api, "description", "test");

        Map<?, ?> data = (Map<?, ?>) api.version().getData();

        assertThat(data.get("version")).isEqualTo("1.3.5");
        assertThat(data.get("buildVersion")).isEqualTo("1.3.5_3");
        assertThat(data.get("qagentVersion")).isEqualTo(QAgentVersion.getVersion());
        assertThat(QAgentVersion.getVersion()).isEqualTo(Files.readString(Path.of("Controlled/version")).trim());
    }

    @Test
    void prefersPublishedQAgentAndPreservesReleaseMetadata() {
        reply("""
                {"frontend":{"version":"2.0-beta"},"backend":{"version":"2.0_1"},
                 "other":{"retained":true},
                 "qagent":{"version":"9.0.0","release_date":"2026-10-30","update_commands":"qa"}}
                """);

        JSONObject data = (JSONObject) api.newVersion().getData();

        assertThat(data.getJSONObject("frontend").getString("version")).isEqualTo("2.0-beta");
        assertThat(data.getJSONObject("backend").getString("version")).isEqualTo("2.0_1");
        assertThat(data.getJSONObject("other").getBoolean("retained")).isTrue();
        JSONObject qagent = data.getJSONObject("qagent");
        assertThat(qagent.getString("version")).isEqualTo("9.0.0");
        assertThat(qagent.getString("release_date")).isEqualTo("2026-10-30");
        assertThat(qagent.getString("update_commands")).isEqualTo("qa");
        assertThat(qagent.getString("source")).isEqualTo("release");
        server.verify();
    }

    @Test
    void oldManifestUsesBundledVersionWithExplicitSource() {
        reply("{\"frontend\":{\"version\":\"1\"},\"backend\":{\"version\":\"2\"}}");

        JSONObject data = (JSONObject) api.newVersion().getData();

        assertThat(data.getJSONObject("frontend").getString("version")).isEqualTo("1");
        assertThat(data.getJSONObject("backend").getString("version")).isEqualTo("2");
        assertThat(data.getJSONObject("qagent").getString("version")).isEqualTo(QAgentVersion.getVersion());
        assertThat(data.getJSONObject("qagent").getString("source")).isEqualTo("bundled");
        server.verify();
    }

    @Test
    void blankPublishedVersionCannotMasqueradeAsLatestRelease() {
        reply("{\"qagent\":{\"version\":\" \",\"description\":\"retained\"}}");

        JSONObject qagent = ((JSONObject) api.newVersion().getData()).getJSONObject("qagent");

        assertThat(qagent.getString("version")).isEqualTo(QAgentVersion.getVersion());
        assertThat(qagent.getString("source")).isEqualTo("bundled");
        assertThat(qagent.getString("description")).isEqualTo("retained");
        server.verify();
    }

    @Test
    void publishedSourceMetadataIsNotOverwritten() {
        reply("{\"qagent\":{\"version\":\"9.0.0\",\"source\":\"custom\"}}");

        JSONObject qagent = ((JSONObject) api.newVersion().getData()).getJSONObject("qagent");

        assertThat(qagent.getString("source")).isEqualTo("custom");
        server.verify();
    }

    private void reply(String body) {
        server.expect(requestTo("https://gitee.com/chuqicloud/soft-ware/raw/master/Cloud/version.json"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}
