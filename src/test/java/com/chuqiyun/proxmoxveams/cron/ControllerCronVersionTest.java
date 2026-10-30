package com.chuqiyun.proxmoxveams.cron;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.utils.QAgentVersion;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    private MasterService masterService;

    @BeforeEach
    void setUp() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
        ReflectionTestUtils.setField(cron, "masterVersion", "1.3.4");
        masterService = mock(MasterService.class);
        when(masterService.update(any(UpdateWrapper.class))).thenReturn(true);
        ReflectionTestUtils.setField(cron, "masterService", masterService);
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

    @Test
    void persistsActualVersionWithoutRewritingOtherNodeFields() {
        node.setQagentVersion("1.0.8");
        node.setName("待保留的节点配置");

        report(versionInfo(" 1.0.9 ", "1.0.1"));

        ArgumentCaptor<UpdateWrapper<Master>> writes = updateCaptor();
        verify(masterService).update(writes.capture());
        UpdateWrapper<Master> update = writes.getValue();
        assertThat(update.getSqlSet()).startsWith("qagent_version=").doesNotContain(",");
        assertThat(update.getSqlSegment()).contains("id =");
        assertThat(update.getParamNameValuePairs()).containsValue(13).containsValue("1.0.9");
        verify(masterService, never()).updateById(any(Master.class));
        assertThat(node.getQagentVersion()).isEqualTo("1.0.9");
    }

    @Test
    void recordsVersionEvenWhenProtocolIsIncompatible() {
        report(versionInfo("1.1.0", "2.0.0"));

        assertThat(node.getQagentVersion()).isEqualTo("1.1.0");
        verify(masterService).update(any(UpdateWrapper.class));
        assertThat(warnings()).singleElement().asString().contains("接口协议不兼容");
    }

    @Test
    void retainsLastVersionWhenReportIsUnavailableBlankOrTooLong() {
        node.setQagentVersion("1.0.8");

        report(null);
        report(versionInfo(null, "1.0.1"));
        report(versionInfo("  ", "1.0.1"));
        report(versionInfo("1".repeat(65), "1.0.1"));

        assertThat(node.getQagentVersion()).isEqualTo("1.0.8");
        verifyNoInteractions(masterService);
    }

    @Test
    void doesNotWriteUnchangedVersionOrClaimFailedPersistenceSucceeded() {
        node.setQagentVersion("1.0.8");
        report(versionInfo("1.0.8", "1.0.1"));
        verifyNoInteractions(masterService);

        when(masterService.update(any(UpdateWrapper.class))).thenReturn(false);
        report(versionInfo("1.0.9", "1.0.1"));

        assertThat(node.getQagentVersion()).isEqualTo("1.0.8");
        assertThat(warnings()).singleElement().asString().contains("保存节点", "版本失败");
    }

    @Test
    void offlineStatusUpdateKeepsLastKnownVersion() {
        node.setQagentVersion("1.0.9");

        ReflectionTestUtils.invokeMethod(cron, "updateControllerStatus", node, 1);

        ArgumentCaptor<UpdateWrapper<Master>> writes = updateCaptor();
        verify(masterService).update(writes.capture());
        assertThat(writes.getValue().getSqlSet()).startsWith("controller_status=").doesNotContain(",");
        assertThat(node.getControllerStatus()).isEqualTo(1);
        assertThat(node.getQagentVersion()).isEqualTo("1.0.9");
    }

    @Test
    void exposesReportedVersionButIgnoresApiSubmittedVersion() throws Exception {
        node.setQagentVersion("1.0.9");
        ObjectMapper mapper = new ObjectMapper();

        assertThat(mapper.readTree(mapper.writeValueAsString(node)).get("qagentVersion").asText())
                .isEqualTo("1.0.9");
        assertThat(mapper.readTree(mapper.writeValueAsString(node)).get("qagentLatestVersion").asText())
                .isEqualTo(QAgentVersion.getVersion());
        assertThat(JSON.parseObject(JSON.toJSONString(node)).getString("qagentVersion")).isEqualTo("1.0.9");
        assertThat(JSON.parseObject(JSON.toJSONString(node)).getString("qagentLatestVersion"))
                .isEqualTo(QAgentVersion.getVersion());
        assertThat(mapper.readValue("{\"qagentVersion\":\"forged\"}", Master.class).getQagentVersion()).isNull();
        assertThat(JSON.parseObject("{\"qagentVersion\":\"forged\"}", Master.class).getQagentVersion()).isNull();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<UpdateWrapper<Master>> updateCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(UpdateWrapper.class);
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
