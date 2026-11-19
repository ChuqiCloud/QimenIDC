package com.chuqiyun.proxmoxveams.security;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent password-login failure guard. The database row is the source of
 * truth so limits apply consistently when more than one manager is running.
 */
@Service
public class LoginAttemptGuard {
    public static final int MAX_FAILURES = 10;
    public static final long WINDOW_MILLIS = 10 * 60 * 1000L;
    public static final long LOCK_MILLIS = 10 * 60 * 1000L;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public LoginAttemptGuard(JdbcTemplate jdbc,
                             org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @PostConstruct
    public void ensureSchema() {
        jdbc.execute("create table if not exists login_attempt_guard ("
                + "guard_key varchar(512) primary key, "
                + "guard_type varchar(16) not null, "
                + "username_key varchar(255) null, "
                + "client_ip varchar(128) not null, "
                + "failure_count int not null default 0, "
                + "window_started bigint not null, "
                + "locked_until bigint not null default 0, "
                + "update_time bigint not null, "
                + "index idx_login_guard_locked_until (locked_until), "
                + "index idx_login_guard_ip (client_ip)"
                + ")");
    }

    public boolean isLocked(String username, String clientIp, boolean knownAccount) {
        long now = System.currentTimeMillis();
        for (String key : keys(username, clientIp, knownAccount)) {
            try {
                Long lockedUntil = jdbc.queryForObject(
                        "select locked_until from login_attempt_guard where guard_key = ?",
                        Long.class, key);
                if (lockedUntil != null && lockedUntil > now) {
                    return true;
                }
            } catch (EmptyResultDataAccessException ignored) {
                // No prior failures for this key.
            }
        }
        return false;
    }

    public void recordFailure(String username, String clientIp, boolean knownAccount) {
        String key = knownAccount ? accountKey(username, clientIp) : ipKey(clientIp);
        String type = knownAccount ? "ACCOUNT_IP" : "IP";
        String normalizedUsername = knownAccount ? normalize(username) : null;
        String normalizedIp = normalizeIp(clientIp);
        long now = System.currentTimeMillis();
        transaction.execute(status -> {
            Integer count = null;
            Long window = null;
            boolean found = false;
            try {
                Long[] values = jdbc.queryForObject(
                        "select failure_count, window_started from login_attempt_guard "
                                + "where guard_key = ? for update",
                        (rs, rowNum) -> new Long[]{rs.getLong(1), rs.getLong(2)}, key);
                if (values != null) {
                    count = values[0].intValue();
                    window = values[1];
                    found = true;
                }
            } catch (EmptyResultDataAccessException ignored) {
                // First failure for this key.
            }
            if (count == null || window == null || now - window >= WINDOW_MILLIS) {
                count = 1;
                window = now;
            } else {
                count++;
            }
            long lockedUntil = count >= MAX_FAILURES ? now + LOCK_MILLIS : 0L;
            if (!found) {
                try {
                    jdbc.update("insert into login_attempt_guard "
                                    + "(guard_key, guard_type, username_key, client_ip, failure_count, window_started, locked_until, update_time) "
                                    + "values (?, ?, ?, ?, ?, ?, ?, ?)",
                            key, type, normalizedUsername, normalizedIp, count, window, lockedUntil, now);
                } catch (org.springframework.dao.DuplicateKeyException duplicate) {
                    // A concurrent first failure inserted the row after our
                    // select. Apply this failure to the row it created.
                    jdbc.update("update login_attempt_guard set failure_count = failure_count + 1, "
                                    + "locked_until = case when failure_count + 1 >= ? then ? else locked_until end, "
                                    + "update_time = ? where guard_key = ?",
                            MAX_FAILURES, now + LOCK_MILLIS, now, key);
                }
            } else {
                jdbc.update("update login_attempt_guard set failure_count = ?, window_started = ?, "
                                + "locked_until = ?, update_time = ? where guard_key = ?",
                        count, window, lockedUntil, now, key);
            }
            return null;
        });
    }

    public void recordSuccess(String username, String clientIp, boolean knownAccount) {
        transaction.execute(status -> {
            if (knownAccount) {
                jdbc.update("delete from login_attempt_guard where guard_key in (?, ?)",
                        accountKey(username, clientIp), ipKey(clientIp));
            } else {
                jdbc.update("delete from login_attempt_guard where guard_key = ?", ipKey(clientIp));
            }
            return null;
        });
    }

    private List<String> keys(String username, String clientIp, boolean knownAccount) {
        List<String> keys = new ArrayList<>();
        if (knownAccount) {
            // An IP lock created by unknown-user probes applies to every
            // account from that source; the account+IP row remains separate
            // so one account cannot lock another source independently.
            keys.add(ipKey(clientIp));
            keys.add(accountKey(username, clientIp));
        } else {
            keys.add(ipKey(clientIp));
        }
        return keys;
    }

    private String accountKey(String username, String clientIp) {
        return "ACCOUNT_IP|" + normalize(username) + "|" + normalizeIp(clientIp);
    }

    private String ipKey(String clientIp) {
        return "IP|" + normalizeIp(clientIp);
    }

    private String normalize(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }

    private String normalizeIp(String value) {
        String ip = normalize(value);
        return ip.length() > 128 ? ip.substring(0, 128) : ip;
    }
}
