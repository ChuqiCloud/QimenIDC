package com.chuqiyun.proxmoxveams.common;

import org.apache.catalina.connector.ClientAbortException;
import org.apache.shiro.ShiroException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExceptionControllerTest {
    private final ExceptionController controller = new ExceptionController();

    @Test
    void globalExceptionUsesGenericMessageAndPreservesStatusCode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("javax.servlet.error.status_code", 409);
        String leakedNodePassword = "node-ssh-password-secret";

        ResponseResult<String> response = controller.globalException(request,
                new IllegalStateException("MyBatis failed for password=" + leakedNodePassword));

        assertEquals(409, response.getCode());
        assertEquals("请求处理失败，请稍后重试", response.getMessage());
        assertFalse(response.getMessage().contains(leakedNodePassword));
        assertNull(response.getData());
    }

    @Test
    void shiroExceptionDoesNotExposeInternalDetails() {
        String leakedNodePassword = "node-pve-password-secret";

        ResponseResult<String> response = controller.handle401(
                new ShiroException("authentication failed with " + leakedNodePassword));

        assertEquals(ResponseResult.RespCode.ERROR.getCode(), response.getCode());
        assertEquals(ResponseResult.RespCode.UNAUTHORIZED.getMessage(), response.getMessage());
        assertFalse(response.getMessage().contains(leakedNodePassword));
    }

    @Test
    void clientAbortStillProducesNoResponseBody() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertNull(controller.globalException(request,
                new RuntimeException("write failed", new ClientAbortException("client disconnected"))));
        assertNull(controller.globalException(request,
                new IOException("Broken pipe")));
    }
}
