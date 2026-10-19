package com.chuqiyun.proxmoxveams.cron;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.entity.Master;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: 星禾
 * @Description: 验证主控按接口协议判断兼容性，避免独立程序版本引发误报
 * @DateTime: 2026/10/19 15:45
 */
class ControllerCronVersionTest {
    private final ControllerCron cron = new ControllerCron();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ControllerCron.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level previousLevel;
    private Master node;

    @BeforeEach
    void setUp() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
        ReflectionTestUtils.setField(cron, "masterVersion", "1.3.4");
        node = new Master();
        node.setId(13);
        node.setName("深圳高防 2");
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
        appender.stop();
    }

    @Test
    void acceptsCompatibleProtocolDespiteDifferentApplicationVersions() {
        report(versionInfo("1.0.3", "1.0.1"));

        assertThat(warnings()).isEmpty();
        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(event.getFormattedMessage()).contains("接口协议兼容", "主控程序=1.3.4", "被控程序=1.0.3");
        });
    }

    @Test
    void warnsAboutIncompatibleProtocolEvenWhenApplicationVersionsMatch() {
        report(versionInfo("1.3.4", "2.0.0"));

        assertThat(warnings()).singleElement().asString()
                .contains("接口协议不兼容", "预期协议=1.0.1", "被控协议=2.0.0", "nodeId=13");
    }

    @Test
    void warnsWhenAgentDoesNotDeclareProtocol() {
        report(versionInfo("1.3.4", null));

        assertThat(warnings()).singleElement().asString().contains("未声明", "被控协议=null");
    }

    @Test
    void reportsUnavailableVersionWithoutClaimingApplicationMismatch() {
        report(null);

        assertThat(warnings()).singleElement().asString().contains("无法获取", "nodeId=13");
    }

    private void report(JSONObject info) {
        ReflectionTestUtils.invokeMethod(cron, "reportAgentCompatibility", node, info);
    }

    private JSONObject versionInfo(String version, String apiVersion) {
        JSONObject info = new JSONObject();
        info.put("version", version);
        info.put("api_version", apiVersion);
        return info;
    }

    private List<String> warnings() {
        return appender.list.stream().filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN))
                .map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList());
    }
}
