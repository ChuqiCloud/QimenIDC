package com.chuqiyun.proxmoxveams.interceptor;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.chuqiyun.proxmoxveams.annotation.AdminApiCheck;
import com.chuqiyun.proxmoxveams.annotation.AdminLoginCheck;
import com.chuqiyun.proxmoxveams.entity.Sysuser;
import com.chuqiyun.proxmoxveams.security.MfaTicketService;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.utils.JWTUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @Author: 星禾
 * @Description: 验证拦截器吊销旧会话并拒绝 MFA 票据
 * @DateTime: 2026/10/17 16:20
 */
class LicenseCheckInterceptorSessionTest {
    private static final String SECRET = "interceptor-test-secret";
    private static final long SESSION = 1_800_000_000_123L;
    private LicenseCheckInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new LicenseCheckInterceptor();
        interceptor.setSecret(SECRET);
        interceptor.setAdminPath("admin");
        Sysuser user = new Sysuser();
        user.setUuid("test-user");
        user.setLogindate(SESSION);
        SysuserService users = mock(SysuserService.class);
        when(users.getSysuserByUuid("test-user")).thenReturn(user);
        ReflectionTestUtils.setField(interceptor, "sysuserService", users);
    }

    @Test
    void apiRejectsPreviousSessionAndAcceptsCurrentOne() throws Exception {
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request(JWTUtil.sign("test-user", SECRET, SESSION - 1)),
                rejected, handler("api")));
        assertTrue(rejected.getContentAsString().contains("501002"));
        assertTrue(interceptor.preHandle(request(JWTUtil.sign("test-user", SECRET, SESSION)),
                new MockHttpServletResponse(), handler("api")));
    }

    @Test
    void pageRejectsPreviousSessionAndAcceptsCurrentOne() throws Exception {
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request(JWTUtil.sign("test-user", SECRET, SESSION - 1)),
                rejected, handler("page")));
        assertEquals("/admin/login", rejected.getRedirectedUrl());
        assertTrue(interceptor.preHandle(request(JWTUtil.sign("test-user", SECRET, SESSION)),
                new MockHttpServletResponse(), handler("page")));
    }

    @Test
    void mfaTicketIsRejectedAsAuthenticationFailure() throws Exception {
        String ticket = new MfaTicketService(SECRET).issue("test-user", "totp");
        MockHttpServletResponse rejected = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request(ticket), rejected, handler("api")));
        assertTrue(rejected.getContentAsString().contains("501002"));
        assertNull(JWTUtil.getTokenData(ticket, SECRET));
        assertNull(JWTUtil.getUsername(ticket, SECRET));
    }

    @Test
    void malformedSessionClaimReturnsNoSession() {
        String signingKey = Base64.getEncoder().encodeToString(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = JWT.create().withClaim("uuid", "test-user").withClaim("secret", "invalid-ciphertext")
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000L))
                .sign(Algorithm.HMAC256(signingKey));

        assertNull(JWTUtil.getTokenData(token, SECRET));
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/profile");
        request.addHeader("Authorization", token);
        return request;
    }

    private HandlerMethod handler(String method) throws NoSuchMethodException {
        return new HandlerMethod(new ProtectedController(), ProtectedController.class.getMethod(method));
    }

    public static class ProtectedController {
        @AdminApiCheck
        public void api() {}

        @AdminLoginCheck
        public void page() {}
    }
}
