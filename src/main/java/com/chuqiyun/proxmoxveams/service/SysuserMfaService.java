package com.chuqiyun.proxmoxveams.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @Author: 星禾
 * @Description: 管理员重置账号安全因子，凭据、策略和会话在同一短事务内更新
 * @DateTime: 2026/11/19 18:26
 */
@Service
public class SysuserMfaService {
    private final JdbcTemplate jdbc;

    public SysuserMfaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean resetTotp(Integer userId) {
        return reset(userId, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean resetPasskey(Integer userId) {
        return reset(userId, false);
    }

    private boolean reset(Integer userId, boolean totp) {
        if (userId == null || userId <= 0) {
            return false;
        }
        List<ResetTarget> targets = jdbc.query(
                "select uuid, logindate from sysuser where id = ? for update",
                (rs, rowNum) -> new ResetTarget(rs.getString("uuid"), rs.getObject("logindate", Long.class)),
                userId);
        if (targets.isEmpty()) {
            return false;
        }
        ResetTarget target = targets.get(0);
        long session = Math.max(System.currentTimeMillis(),
                target.session() == null ? 0L : Math.addExact(target.session(), 1L));
        if (totp) {
            jdbc.update("update sysuser set totp_secret = null, totp_confirmed_time = null, "
                    + "totp_last_step = null, mfa_type = case when mfa_type = 'totp' then 'none' else mfa_type end, "
                    + "logindate = ? where id = ?", session, userId);
        } else {
            jdbc.update("update sysuser set passkey_enabled = 0, "
                    + "mfa_type = case when mfa_type = 'passkey' then 'none' else mfa_type end, "
                    + "logindate = ? where id = ?", session, userId);
            jdbc.update("delete from sys_passkey where userid = ?", userId);
        }
        // 清除该账号已签发的注册/认证挑战，防止重置前的注册请求重新写回旧凭据。
        jdbc.update("delete from sys_passkey_challenge where user_uuid = ?", target.uuid());
        return true;
    }

    private record ResetTarget(String uuid, Long session) {
    }
}
