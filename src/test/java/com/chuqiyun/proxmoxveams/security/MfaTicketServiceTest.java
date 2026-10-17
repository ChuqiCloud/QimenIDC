package com.chuqiyun.proxmoxveams.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MfaTicketServiceTest {
    private final MfaTicketService service = new MfaTicketService("unit-test-secret");

    @Test
    void issuesTicketBoundToUuidAndMethod() {
        String ticket = service.issue("user-uuid", "totp");

        assertEquals("user-uuid", service.resolveUuid(ticket, "totp"));
        // 票据不能跨二步验证方式复用
        assertNull(service.resolveUuid(ticket, "passkey"));
        assertEquals(5 * 60 * 1000L, service.getTicketExpireMillis());
    }

    @Test
    void rejectsForeignAndBlankTickets() {
        String ticket = service.issue("user-uuid", "passkey");
        MfaTicketService other = new MfaTicketService("another-secret");

        assertNull(other.resolveUuid(ticket, "passkey"));
        assertNull(service.resolveUuid("", "passkey"));
        assertNull(service.resolveUuid(null, "passkey"));
        assertTrue(service.resolveUuid(ticket, "passkey") != null);
    }

    @Test
    void ticketClaimsDoNotCarryLoginSecret() {
        String ticket = service.issue("user-uuid", "totp");
        com.auth0.jwt.interfaces.DecodedJWT jwt = com.auth0.jwt.JWT.decode(ticket);

        // 票据不能包含正式 token 才有的 secret 声明，无法被当作登录凭证使用
        assertNull(jwt.getClaim("secret").asString());
        assertEquals("mfa", jwt.getClaim("scope").asString());
    }
}
