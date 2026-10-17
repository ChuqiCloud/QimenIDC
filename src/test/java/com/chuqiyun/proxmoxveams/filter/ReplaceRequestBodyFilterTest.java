package com.chuqiyun.proxmoxveams.filter;

import com.chuqiyun.proxmoxveams.entity.SystemLog;
import com.chuqiyun.proxmoxveams.service.SystemLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 密码字段仅对日志快照脱敏，保持业务请求与响应内容不变。
 *
 * @author 星禾
 */
class ReplaceRequestBodyFilterTest {
    @Test
    void masksPasswordVariantsWithoutChangingOtherAuditFields() throws Exception {
        String requestBody = "{"
                + "\"name\":\"node-one\","
                + "\"newPassword\":\"new-secret\","
                + "\"SSH_password\":\"ssh-secret\","
                + "\"PVE-Password\":\"pve-secret\","
                + "\"csrfToken\":\"csrf-secret\","
                + "\"ticket\":\"ticket-secret\","
                + "\"nested\":{\"pve_password\":\"nested-secret\",\"region\":\"cn\"},"
                + "\"items\":[{\"new-password\":\"list-secret\",\"id\":7}]"
                + "}";

        String sanitized = sanitize(requestBody);

        assertTrue(sanitized.contains("node-one"));
        assertTrue(sanitized.contains("\"region\":\"cn\""));
        assertTrue(sanitized.contains("\"id\":7"));
        assertFalse(sanitized.contains("new-secret"));
        assertFalse(sanitized.contains("ssh-secret"));
        assertFalse(sanitized.contains("pve-secret"));
        assertFalse(sanitized.contains("csrf-secret"));
        assertFalse(sanitized.contains("ticket-secret"));
        assertFalse(sanitized.contains("nested-secret"));
        assertFalse(sanitized.contains("list-secret"));
        assertTrue(sanitized.contains("\"newPassword\":\"***\""));
        assertTrue(sanitized.contains("\"SSH_password\":\"***\""));
    }

    @Test
    void masksCaseAndSeparatorVariantsAndLeavesOriginalBodyUntouched() throws Exception {
        String requestBody = "{\"PVE_PASSWORD\":\"one\",\"pve-password\":\"two\",\"PvePassword\":\"three\",\"CSRF_TOKEN\":\"four\",\"TICKET\":\"five\",\"label\":\"keep\"}";

        String sanitized = sanitize(requestBody);

        assertTrue(sanitized.contains("\"label\":\"keep\""));
        assertFalse(sanitized.contains("one"));
        assertFalse(sanitized.contains("two"));
        assertFalse(sanitized.contains("three"));
        assertFalse(sanitized.contains("four"));
        assertFalse(sanitized.contains("five"));
        assertTrue(requestBody.contains("one"));
        assertTrue(requestBody.contains("two"));
        assertTrue(requestBody.contains("three"));
    }

    @Test
    void filterPreservesHttpPayloadAndStoresOnlySanitizedSnapshots() throws Exception {
        ReplaceRequestBodyFilter filter = new ReplaceRequestBodyFilter();
        SystemLogService systemLogService = mock(SystemLogService.class);
        ReflectionTestUtils.setField(filter, "systemLogService", systemLogService);
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/pve/updateVmConfig/restPassword");
        request.setContentType("application/json");
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String requestBody = "{\"hostId\":7,\"newPassword\":\"submitted-secret\"}";
        String responseBody = "{\"code\":20000,\"data\":{\"PVE_password\":\"response-secret\",\"name\":\"node-one\"}}";
        request.setContent(requestBody.getBytes(StandardCharsets.UTF_8));
        request.setQueryString("hostId=7&SSH_password=query-secret&name=node%20one");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (businessRequest, businessResponse) -> {
            assertEquals(requestBody, businessRequest.getReader().lines().collect(Collectors.joining("\n")));
            businessResponse.setContentType("application/json");
            businessResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
            businessResponse.getWriter().write(responseBody);
        });

        assertEquals(responseBody, response.getContentAsString(StandardCharsets.UTF_8));
        ArgumentCaptor<SystemLog> logCaptor = ArgumentCaptor.forClass(SystemLog.class);
        verify(systemLogService).saveSystemLogAsync(logCaptor.capture());
        SystemLog log = logCaptor.getValue();
        assertFalse(log.getRequestBody().contains("submitted-secret"));
        assertFalse(log.getResponseBody().contains("response-secret"));
        assertTrue(log.getRequestBody().contains("\"hostId\":7"));
        assertTrue(log.getResponseBody().contains("node-one"));
        assertEquals("hostId=7&SSH_password=***&name=node%20one", log.getQueryString());
        assertEquals("hostId=7&SSH_password=query-secret&name=node%20one", request.getQueryString());
    }

    private String sanitize(String requestBody) throws Exception {
        ReplaceRequestBodyFilter filter = new ReplaceRequestBodyFilter();
        Method method = ReplaceRequestBodyFilter.class.getDeclaredMethod("sanitizeRequestBody", String.class);
        method.setAccessible(true);
        return (String) method.invoke(filter, requestBody);
    }
}
