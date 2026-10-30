package com.chuqiyun.proxmoxveams.service;

import com.chuqiyun.proxmoxveams.entity.Master;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * @Author: 星禾
 * @Description: 验证最新 QAgent 版本仅来自线上清单，批量节点查询一次且不保存旧发布版本
 * @DateTime: 2026/10/30 13:55
 */
class ReleaseVersionServiceTest {
    private static final String RELEASE_URL = "https://gitee.com/chuqicloud/soft-ware/raw/master/Cloud/version.json";
    private final ReleaseVersionService service = new ReleaseVersionService();
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestTemplate template = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        server = MockRestServiceServer.bindTo(template).build();
    }

    @Test
    void eachQueryReadsTheCurrentPublishedVersion() {
        reply("{\"qagent\":{\"version\":\" 9.0.0 \"}}");
        reply("{\"qagent\":{\"version\":\"9.0.1\"}}");

        assertThat(service.getQagentVersion()).isEqualTo("9.0.0");
        assertThat(service.getQagentVersion()).isEqualTo("9.0.1");
        server.verify();
    }

    @Test
    void missingBlankAndMalformedVersionNeverUseALocalFallback() {
        for (String body : List.of("{}", "{\"qagent\":{}}", "{\"qagent\":{\"version\":\" \"}}",
                "{\"qagent\":\"invalid\"}", "{\"qagent\":{\"version\":123}}", "not-json")) {
            reply(body);
        }

        for (int i = 0; i < 6; i++) {
            assertThat(service.getQagentVersion()).isNull();
        }
        server.verify();
    }

    @Test
    void unavailableManifestDoesNotReuseAnEarlierPublishedVersion() {
        reply("{\"qagent\":{\"version\":\"9.0.0\"}}");
        server.expect(requestTo(RELEASE_URL)).andRespond(withServerError());

        assertThat(service.getQagentVersion()).isEqualTo("9.0.0");
        assertThat(service.getQagentVersion()).isNull();
        server.verify();
    }

    @Test
    void fillsAPageUsingOneRequestAndPreservesActualVersions() {
        Master first = new Master();
        first.setQagentVersion("1.0.8");
        Master second = new Master();
        second.setQagentVersion("1.0.9");
        reply("{\"qagent\":{\"version\":\"9.0.0\"}}");

        service.fillQagentLatestVersion(List.of(first, second));

        assertThat(first.getQagentLatestVersion()).isEqualTo("9.0.0");
        assertThat(second.getQagentLatestVersion()).isEqualTo("9.0.0");
        assertThat(first.getQagentVersion()).isEqualTo("1.0.8");
        assertThat(second.getQagentVersion()).isEqualTo("1.0.9");
        server.verify();
    }

    @Test
    void emptyPagesDoNotRequestTheManifest() {
        service.fillQagentLatestVersion(List.of());
        service.fillQagentLatestVersion(null);

        server.verify();
    }

    private void reply(String body) {
        server.expect(requestTo(RELEASE_URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}
