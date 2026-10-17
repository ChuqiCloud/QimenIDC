package com.chuqiyun.proxmoxveams.cron;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: 星禾
 * @Description: 验证默认关闭外部服务信息上报
 * @DateTime: 2026/10/17 15:52
 */
class ReportCronConfigurationTest {

    @Test
    void reportingIsDisabledWhenNoOptInPropertyIsSet() {
        new ApplicationContextRunner()
                .withUserConfiguration(ReportCron.class)
                .run(context -> assertThat(context).doesNotHaveBean(ReportCron.class));
    }

    @Test
    void reportingWithoutExplicitPlatformKeyDoesNotContactExternalServices() {
        ReportCron report = new ReportCron();
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(report, "restTemplate", restTemplate);

        report.report();

        verifyNoInteractions(restTemplate);
    }
}
