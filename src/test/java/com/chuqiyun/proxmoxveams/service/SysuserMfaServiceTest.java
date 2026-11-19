package com.chuqiyun.proxmoxveams.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @Author: 星禾
 * @Description: 验证管理员重置安全因子时的账号隔离、会话吊销和事务回滚
 * @DateTime: 2026/11/19 18:28
 */
class SysuserMfaServiceTest {
    private static final int TARGET = 1;
    private static final int OTHER = 2;
    private JdbcTemplate jdbc;
    private SysuserMfaService service;
    private long targetSession;

    @BeforeEach
    void setup() {
        DriverManagerDataSource source = new DriverManagerDataSource("jdbc:h2:mem:reset_mfa_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("create table sysuser (id int primary key, uuid varchar(255) not null, "
                + "logindate varchar(255), mfa_type varchar(16), totp_secret text, "
                + "totp_confirmed_time bigint, totp_last_step bigint, passkey_enabled int)");
        jdbc.execute("create table sys_passkey (id bigint primary key, userid int not null)");
        jdbc.execute("create table sys_passkey_challenge (challenge varchar(64) primary key, type varchar(24), "
                + "user_uuid varchar(64), expires_at bigint)");
        targetSession = System.currentTimeMillis() + 60_000L;
        jdbc.update("insert into sysuser values (?, 'target', ?, 'totp', 'target-totp', 20, 30, 1)",
                TARGET, targetSession);
        jdbc.update("insert into sysuser values (?, 'other', 40, 'passkey', 'other-totp', 50, 60, 1)", OTHER);
        jdbc.update("insert into sys_passkey values (1, 1), (2, 1), (3, 2)");
        jdbc.update("insert into sys_passkey_challenge values ('target-register', 'registration', 'target', 123), "
                + "('target-auth', 'authentication', 'target', 123), ('other-auth', 'authentication', 'other', 123), "
                + "('discoverable', 'authentication', null, 123)");

        ProxyFactory proxy = new ProxyFactory(new SysuserMfaService(jdbc));
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),
                new AnnotationTransactionAttributeSource()));
        service = (SysuserMfaService) proxy.getProxy();
    }

    @AfterEach
    void cleanup() {
        jdbc.execute("drop all objects");
    }

    @Test
    void totpResetClearsOnlyTargetTotpAndItsPolicy() {
        assertTrue(service.resetTotp(TARGET));

        assertNull(targetValue("totp_secret", String.class));
        assertNull(targetValue("totp_confirmed_time", Long.class));
        assertNull(targetValue("totp_last_step", Long.class));
        assertEquals("none", targetValue("mfa_type", String.class));
        assertEquals(targetSession + 1, targetValue("logindate", Long.class));
        assertEquals(1, targetValue("passkey_enabled", Integer.class));
        assertEquals(2, countPasskeys(TARGET));
        assertEquals(1, countPasskeys(OTHER));
        assertEquals("other-totp", jdbc.queryForObject("select totp_secret from sysuser where id = ?", String.class, OTHER));
    }

    @Test
    void passkeyResetClearsAllTargetKeysAndChallengesButKeepsTotpPolicy() {
        assertTrue(service.resetPasskey(TARGET));

        assertEquals(0, countPasskeys(TARGET));
        assertEquals(0, targetValue("passkey_enabled", Integer.class));
        assertEquals(0, countTargetChallenges());
        assertEquals(2, jdbc.queryForObject("select count(*) from sys_passkey_challenge", Integer.class));
        assertEquals(1, countPasskeys(OTHER));
        assertEquals("totp", targetValue("mfa_type", String.class));
        assertEquals("target-totp", targetValue("totp_secret", String.class));
        assertEquals(20L, targetValue("totp_confirmed_time", Long.class));
        assertEquals(30L, targetValue("totp_last_step", Long.class));
        assertEquals(targetSession + 1, targetValue("logindate", Long.class));
        assertEquals(40L, jdbc.queryForObject("select logindate from sysuser where id = ?", Long.class, OTHER));
    }

    @Test
    void inactiveFactorResetPreservesTheActivePolicyAndRepeatResetSucceeds() {
        jdbc.update("update sysuser set mfa_type = 'passkey' where id = ?", TARGET);
        assertTrue(service.resetTotp(TARGET));
        assertEquals("passkey", targetValue("mfa_type", String.class));

        assertTrue(service.resetPasskey(TARGET));
        assertEquals("none", targetValue("mfa_type", String.class));
        assertTrue(service.resetPasskey(TARGET));
        assertFalse(service.resetPasskey(3));
    }

    @Test
    void challengeCleanupFailureRollsBackCredentialDeletionAndSessionRotation() {
        jdbc.execute("create table challenge_reference (challenge varchar(64) references sys_passkey_challenge(challenge))");
        jdbc.update("insert into challenge_reference values ('target-register')");
        jdbc.update("update sysuser set mfa_type = 'passkey' where id = ?", TARGET);

        assertThrows(DataIntegrityViolationException.class, () -> service.resetPasskey(TARGET));

        assertEquals(2, countPasskeys(TARGET));
        assertEquals(2, countTargetChallenges());
        assertEquals(1, targetValue("passkey_enabled", Integer.class));
        assertEquals("passkey", targetValue("mfa_type", String.class));
        assertEquals(targetSession, targetValue("logindate", Long.class));
    }

    private <T> T targetValue(String column, Class<T> type) {
        return jdbc.queryForObject("select " + column + " from sysuser where id = ?", type, TARGET);
    }

    private int countPasskeys(int userId) {
        return jdbc.queryForObject("select count(*) from sys_passkey where userid = ?", Integer.class, userId);
    }

    private int countTargetChallenges() {
        return jdbc.queryForObject("select count(*) from sys_passkey_challenge where user_uuid = 'target'", Integer.class);
    }
}
