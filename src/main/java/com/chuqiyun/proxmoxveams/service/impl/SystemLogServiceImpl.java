package com.chuqiyun.proxmoxveams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
import com.chuqiyun.proxmoxveams.dao.SystemLogDao;
import com.chuqiyun.proxmoxveams.entity.SystemLog;
import com.chuqiyun.proxmoxveams.service.SystemLogService;
import org.apache.commons.lang3.StringUtils;
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
    @Override
    @Async("taskExecutor")
    public void saveSystemLogAsync(SystemLog systemLog) {
        if (Objects.isNull(systemLog)) {
            return;
        }
        try {
            if (Objects.isNull(systemLog.getCreateTime())) {
                systemLog.setCreateTime(System.currentTimeMillis());
            }
            this.save(systemLog);
        } catch (Exception e) {
            UnifiedLogger.warn(UnifiedLogger.LogType.SYSTEM, "系统日志写入数据库失败: {}", e.getMessage());
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
}
