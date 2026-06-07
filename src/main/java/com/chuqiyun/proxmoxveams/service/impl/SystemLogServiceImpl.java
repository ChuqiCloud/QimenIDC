package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.dao.SystemLogDao;
import com.chuqiyun.proxmoxveams.entity.SystemLog;
import com.chuqiyun.proxmoxveams.service.SystemLogService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * @Author: 星禾
 * @Description: 系统日志服务实现
 * @DateTime: 2026/6/7 11:18
 */
@Service("systemLogService")
public class SystemLogServiceImpl extends ServiceImpl<SystemLogDao, SystemLog> implements SystemLogService {
    private static final int DEFAULT_RETENTION_DAYS = 180;
    private static final int DELETE_BATCH_SIZE = 10000;

    @Value("${config.system_log_retention_days:}")
    private String systemLogRetentionDays;

    @Override
    @Async("taskExecutor")
    public void saveSystemLogAsync(SystemLog systemLog) {
        if (Objects.isNull(systemLog)) {
            return;
        }
        try {
            normalizeSystemLog(systemLog);
            if (Objects.isNull(systemLog.getCreateTime())) {
                systemLog.setCreateTime(System.currentTimeMillis());
            }
            this.save(systemLog);
        } catch (Exception ignored) {
        }
    }

    @Override
    public Page<SystemLog> getSystemLogs(Integer page,
                                         Integer size,
                                         String type,
                                         String level,
                                         String keyword,
                                         String requestId,
                                         String date) {
        Page<SystemLog> systemLogPage = new Page<>(normalizePage(page), normalizeSize(size));
        QueryWrapper<SystemLog> queryWrapper = new QueryWrapper<>();
        String normalizedType = normalizeType(type);
        String normalizedLevel = normalizeLevel(level);
        if (StringUtils.isNotBlank(normalizedType)) {
            queryWrapper.eq("log_type", normalizedType);
        }
        if (StringUtils.isNotBlank(normalizedLevel)) {
            queryWrapper.eq("level", normalizedLevel);
        }
        if (StringUtils.isNotBlank(requestId)) {
            queryWrapper.eq("request_id", StringUtils.trim(requestId));
        }
        if (StringUtils.isNotBlank(keyword)) {
            String normalizedKeyword = StringUtils.trim(keyword);
            queryWrapper.and(wrapper -> wrapper.like("content", normalizedKeyword)
                    .or().like("uri", normalizedKeyword)
                    .or().like("operator", normalizedKeyword)
                    .or().like("client_ip", normalizedKeyword)
                    .or().like("query_string", normalizedKeyword)
                    .or().like("request_body", normalizedKeyword)
                    .or().like("response_body", normalizedKeyword)
                    .or().like("handler", normalizedKeyword)
                    .or().like("business_message", normalizedKeyword)
                    .or().like("exception", normalizedKeyword));
        }
        appendDateCondition(queryWrapper, date);
        queryWrapper.orderByDesc("create_time");
        return this.page(systemLogPage, queryWrapper);
    }

    /**
     * @Author: 星禾
     * @Description: 按配置分页清理过期系统日志
     * @DateTime: 2026/6/7 10:49
     * @Return int 总共删除的记录数
     */
    @Override
    public int deleteExpiredSystemLogs() {
        int totalDeleted = 0;
        int normalizedRetentionDays = resolveRetentionDays(systemLogRetentionDays, DEFAULT_RETENTION_DAYS);
        long expireTime = System.currentTimeMillis() - normalizedRetentionDays * 24L * 60 * 60 * 1000;

        while (true) {
            QueryWrapper<SystemLog> queryWrapper = new QueryWrapper<>();
            queryWrapper.lt("create_time", expireTime);
            queryWrapper.last("LIMIT " + DELETE_BATCH_SIZE);

            int deleted = this.baseMapper.delete(queryWrapper);
            totalDeleted += deleted;
            if (deleted < DELETE_BATCH_SIZE) {
                break;
            }
            if (!sleepQuietly()) {
                break;
            }
        }

        return totalDeleted;
    }

    private void appendDateCondition(QueryWrapper<SystemLog> queryWrapper, String date) {
        if (StringUtils.isBlank(date)) {
            return;
        }
        try {
            LocalDate queryDate = LocalDate.parse(StringUtils.trim(date));
            long startTime = queryDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long endTime = queryDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1;
            queryWrapper.between("create_time", startTime, endTime);
        } catch (Exception ignored) {
        }
    }

    private String normalizeType(String type) {
        String normalizedType = StringUtils.upperCase(StringUtils.trimToEmpty(type));
        if (StringUtils.isBlank(normalizedType) || "ALL".equals(normalizedType)) {
            return "";
        }
        return normalizedType;
    }

    private String normalizeLevel(String level) {
        String normalizedLevel = StringUtils.upperCase(StringUtils.trimToEmpty(level));
        if (StringUtils.isBlank(normalizedLevel) || "ALL".equals(normalizedLevel)) {
            return "";
        }
        return normalizedLevel;
    }

    private int normalizePage(Integer page) {
        if (Objects.isNull(page) || page <= 0) {
            return 1;
        }
        return page;
    }

    private int normalizeSize(Integer size) {
        if (Objects.isNull(size) || size <= 0) {
            return 20;
        }
        return Math.min(size, 200);
    }

    private int resolveRetentionDays(String configValue, int defaultDays) {
        if (StringUtils.isBlank(configValue)) {
            return defaultDays;
        }
        try {
            return normalizeRetentionDays(Integer.parseInt(StringUtils.trim(configValue)), defaultDays);
        } catch (NumberFormatException ignored) {
            return defaultDays;
        }
    }

    private int normalizeRetentionDays(Integer retentionDays, int defaultDays) {
        if (Objects.isNull(retentionDays) || retentionDays <= 0) {
            return defaultDays;
        }
        return retentionDays;
    }

    private boolean sleepQuietly() {
        try {
            Thread.sleep(100);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void normalizeSystemLog(SystemLog systemLog) {
        systemLog.setRequestId(truncate(systemLog.getRequestId(), 64));
        systemLog.setLogType(truncate(systemLog.getLogType(), 32));
        systemLog.setLevel(truncate(systemLog.getLevel(), 16));
        systemLog.setMethod(truncate(systemLog.getMethod(), 16));
        systemLog.setUri(truncate(systemLog.getUri(), 255));
        systemLog.setPathPattern(truncate(systemLog.getPathPattern(), 255));
        systemLog.setHandler(truncate(systemLog.getHandler(), 255));
        systemLog.setClientIp(truncate(systemLog.getClientIp(), 64));
        systemLog.setOperator(truncate(systemLog.getOperator(), 255));
        systemLog.setAuthType(truncate(systemLog.getAuthType(), 32));
        systemLog.setQueryString(truncate(systemLog.getQueryString(), 2000));
        systemLog.setRequestBody(truncate(systemLog.getRequestBody(), 4000));
        systemLog.setBusinessMessage(truncate(systemLog.getBusinessMessage(), 255));
        systemLog.setResponseBody(truncate(systemLog.getResponseBody(), 4000));
        systemLog.setException(truncate(systemLog.getException(), 2000));
        systemLog.setContent(truncate(systemLog.getContent(), 4000));
    }

    private String truncate(String value, int maxLength) {
        if (StringUtils.isEmpty(value)) {
            return value;
        }
        return StringUtils.left(value, maxLength);
    }
}
