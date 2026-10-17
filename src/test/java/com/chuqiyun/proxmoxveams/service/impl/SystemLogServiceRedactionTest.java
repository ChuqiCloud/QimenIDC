package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuqiyun.proxmoxveams.entity.SystemLog;
import com.chuqiyun.proxmoxveams.security.StoredLogRedactor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.Serializable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 日志保存和历史日志查询均需阻止凭据回显。
 *
 * @author 星禾
 */
class SystemLogServiceRedactionTest {
    @Test
    void sanitizesEverySensitiveSnapshotBeforeSaving() {
        SystemLogServiceImpl service = spy(new SystemLogServiceImpl());
        doReturn(true).when(service).save(any(SystemLog.class));

        service.saveSystemLogAsync(logWithSecrets());

        ArgumentCaptor<SystemLog> captor = ArgumentCaptor.forClass(SystemLog.class);
        verify(service).save(captor.capture());
        assertSanitized(captor.getValue());
    }

    @Test
    void sanitizesHistoricalDetailBeforeReturningIt() {
        SystemLogServiceImpl service = spy(new SystemLogServiceImpl());
        doReturn(logWithSecrets()).when(service).getById((Serializable) 7);

        assertSanitized(service.getSystemLogById(7));
        assertNull(service.getSystemLogById(0));
    }

    @Test
    void sanitizesHistoricalPageBeforeReturningIt() {
        SystemLogServiceImpl service = spy(new SystemLogServiceImpl());
        Page<SystemLog> page = new Page<>(1, 20);
        page.setRecords(List.of(logWithSecrets()));
        doReturn(page).when(service).page(any(Page.class), any(Wrapper.class));

        Page<SystemLog> result = service.getSystemLogs(1, 20, "all", "all", null, null, null, null);

        assertSanitized(result.getRecords().get(0));
    }

    private SystemLog logWithSecrets() {
        SystemLog log = new SystemLog();
        log.setId(7);
        log.setUri("/admin/updateNodeInfo");
        log.setQueryString("hostId=7&ssh_password=query-secret&label=keep");
        log.setRequestBody("{\"name\":\"node-one\",\"password\":\"request-secret\"}");
        log.setResponseBody("{\"data\":{\"csrfToken\":\"response-secret\",\"count\":2}}");
        log.setBusinessMessage("update failed: PVE_password=business-secret");
        log.setException("IllegalStateException: newPassword=exception-secret");
        log.setContent("UPDATE failed: ticket=content-secret");
        return log;
    }

    private void assertSanitized(SystemLog log) {
        assertEquals("/admin/updateNodeInfo", log.getUri());
        assertEquals("hostId=7&ssh_password=***&label=keep", log.getQueryString());
        assertEquals("{\"name\":\"node-one\",\"password\":\"***\"}", log.getRequestBody());
        assertEquals("{\"data\":{\"csrfToken\":\"***\",\"count\":2}}", log.getResponseBody());
        assertEquals(StoredLogRedactor.SENSITIVE_TEXT_PLACEHOLDER, log.getBusinessMessage());
        assertEquals(StoredLogRedactor.SENSITIVE_TEXT_PLACEHOLDER, log.getException());
        assertEquals(StoredLogRedactor.SENSITIVE_TEXT_PLACEHOLDER, log.getContent());
        assertFalse(log.toString().contains("-secret"));
    }
}
