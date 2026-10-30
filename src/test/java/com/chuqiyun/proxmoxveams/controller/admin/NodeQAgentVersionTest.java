package com.chuqiyun.proxmoxveams.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.entity.Master;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.ReleaseVersionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 节点列表以一次线上发布查询填充最新版本，同时保留各节点实际运行版本。
 *
 * @author 星禾
 */
class NodeQAgentVersionTest {
    private final MasterService masters = mock(MasterService.class);
    private final ReleaseVersionService releases = mock(ReleaseVersionService.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void nodePageReturnsOnlineLatestVersionAlongsideEachReportedVersion() throws Exception {
        Master first = node(13, "1.0.8");
        Master second = node(14, "1.0.9");
        Page<Master> page = page(first, second);
        when(masters.getMasterList(1, 20)).thenReturn(page);
        fillWithPublishedVersion("1.2.0");
        SysNodeMasterController controller = new SysNodeMasterController();
        ReflectionTestUtils.setField(controller, "masterService", masters);
        ReflectionTestUtils.setField(controller, "releaseVersionService", releases);

        JsonNode response = mapper.valueToTree(controller.selectNodeByPage(1, 20));
        JsonNode records = response.path("data").path("records");

        assertThat(records.get(0).path("qagentVersion").asText()).isEqualTo("1.0.8");
        assertThat(records.get(1).path("qagentVersion").asText()).isEqualTo("1.0.9");
        assertThat(records.get(0).path("qagentLatestVersion").asText()).isEqualTo("1.2.0");
        assertThat(records.get(1).path("qagentLatestVersion").asText()).isEqualTo("1.2.0");
        verify(releases).fillQagentLatestVersion(page.getRecords());
        verifyNoMoreInteractions(releases);
    }

    @Test
    void areaPageReturnsUnknownLatestVersionWithoutInventingLocalFallback() throws Exception {
        Master first = node(13, "1.0.8");
        Master unreported = node(14, null);
        Page<Master> page = page(first, unreported);
        when(masters.getMasterList(eq(1), eq(20), any(QueryWrapper.class))).thenReturn(page);
        fillWithPublishedVersion(null);
        SysGroupController controller = new SysGroupController();
        ReflectionTestUtils.setField(controller, "masterService", masters);
        ReflectionTestUtils.setField(controller, "releaseVersionService", releases);

        JsonNode response = mapper.valueToTree(controller.getNodeListByArea(7, 1, 20));
        JsonNode records = response.path("data").path("records");

        assertThat(records.get(0).path("qagentVersion").asText()).isEqualTo("1.0.8");
        assertThat(records.get(1).path("qagentVersion").isNull()).isTrue();
        assertThat(records.get(0).path("qagentLatestVersion").isNull()).isTrue();
        assertThat(records.get(1).path("qagentLatestVersion").isNull()).isTrue();
        verify(releases).fillQagentLatestVersion(page.getRecords());
        verifyNoMoreInteractions(releases);
    }

    private void fillWithPublishedVersion(String version) {
        doAnswer(invocation -> {
            Collection<Master> nodes = invocation.getArgument(0);
            nodes.forEach(node -> node.setQagentLatestVersion(version));
            return null;
        }).when(releases).fillQagentLatestVersion(any());
    }

    private Master node(Integer id, String reportedVersion) {
        Master node = new Master();
        node.setId(id);
        node.setQagentVersion(reportedVersion);
        return node;
    }

    private Page<Master> page(Master... nodes) {
        Page<Master> page = new Page<>(1, 20);
        page.setRecords(List.of(nodes));
        page.setTotal(nodes.length);
        return page;
    }
}
