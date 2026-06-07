package com.chuqiyun.proxmoxveams.filter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.entity.SystemLog;
import com.chuqiyun.proxmoxveams.entity.Sysuser;
import com.chuqiyun.proxmoxveams.service.SystemLogService;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.utils.ServletUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.annotation.Resource;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * @author mryunqi
 * @date 2023/8/10
 */
@Component
public class ReplaceRequestBodyFilter extends OncePerRequestFilter {
    private static final int MAX_LOG_BODY_LENGTH = 4096;
    private static final int MAX_AUDIT_CONTENT_LENGTH = 1024;
    private static final int MAX_AUDIT_VALUE_LENGTH = 128;
    private static final int MAX_AUDIT_FIELDS = 8;
    private static final Pattern JWT_PATTERN = Pattern.compile("\\b[A-Za-z0-9\\-_]+=*\\.[A-Za-z0-9\\-_]+=*\\.[A-Za-z0-9\\-_]+=*\\b");
    private static final Set<String> SENSITIVE_KEYS = new HashSet<>(Arrays.asList(
            "password", "passwd", "pwd", "token", "authorization", "cookie",
            "secret", "appkey", "csrfpreventiontoken", "ticket"
    ));
    private static final List<String> AUDIT_KEYWORDS = Arrays.asList(
            "id", "uuid", "vmid", "nodeid", "hostid", "poolid", "ip", "hostname",
            "username", "name", "status", "port", "type", "email", "phone"
    );

    @Resource
    private SysuserService sysuserService;

    @Resource
    private SystemLogService systemLogService;

    @Value("${config.secret}")
    private String secret;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-Id", requestId);
        long startTime = System.nanoTime();
        RequestWrapper requestWrapper = shouldWrapRequest(request) ? new RequestWrapper(request) : null;
        HttpServletRequest requestToUse = Objects.nonNull(requestWrapper) ? requestWrapper : request;
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        Throwable throwable = null;
        try {
            chain.doFilter(requestToUse, responseWrapper);
        } catch (Exception ex) {
            throwable = ex;
            throw ex;
        } finally {
            try {
                logRequestResult(requestToUse, requestWrapper, responseWrapper, requestId, startTime, throwable);
            } catch (Exception ignored) {
            } finally {
                responseWrapper.copyBodyToResponse();
                MDC.remove("requestId");
            }
        }
    }

    private boolean shouldWrapRequest(HttpServletRequest request) {
        if (request instanceof RequestWrapper) {
            return false;
        }
        String contentType = request.getContentType();
        return StringUtils.isBlank(contentType) || !StringUtils.containsIgnoreCase(contentType, "multipart/form-data");
    }

    private void logRequestResult(HttpServletRequest request,
                                  RequestWrapper requestWrapper,
                                  ContentCachingResponseWrapper responseWrapper,
                                  String requestId,
                                  long startTime,
                                  Throwable throwable) {
        if (shouldSkipAuditLog(request)) {
            return;
        }
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        String requestBody = Objects.nonNull(requestWrapper) ? sanitizeRequestBody(requestWrapper.getBody()) : "";
        String responseBody = sanitizeResponseBody(request.getRequestURI(), getResponseBody(responseWrapper));
        Integer businessCode = extractBusinessCode(responseBody);
        String businessMessage = extractBusinessMessage(responseBody);
        String clientIp = getClientIp(request);
        String operator = resolveOperator(request);
        String authType = resolveAuthType(request);
        String pathPattern = resolvePathPattern(request);
        String handler = resolveHandler(request);
        String level = resolveLogLevel(responseWrapper.getStatus(), businessCode, throwable);
        String auditContent = buildAuditContent(request, level, businessMessage, throwable, requestBody);
        saveAccessLogToDatabase(requestId, level, request, pathPattern, handler, clientIp, operator, authType,
                responseWrapper.getStatus(), businessCode, durationMs, throwable, auditContent);
    }

    private String resolvePathPattern(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return Objects.isNull(attribute) ? "" : String.valueOf(attribute);
    }

    private String resolveHandler(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (attribute instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) attribute;
            return handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName();
        }
        return "";
    }

    private String resolveOperator(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StringUtils.isBlank(authorization)) {
            return "anonymous";
        }
        if (StringUtils.startsWithIgnoreCase(authorization, "Basic ")) {
            try {
                String decoded = new String(
                        Base64.getDecoder().decode(StringUtils.substringAfter(authorization, "Basic ").trim()),
                        StandardCharsets.UTF_8
                );
                return "api:" + StringUtils.defaultIfBlank(StringUtils.substringBefore(decoded, ":"), "unknown");
            } catch (IllegalArgumentException ignored) {
                return "api:unknown";
            }
        }
        try {
            Sysuser loginUser = ServletUtil.getSysLoginMember(request, secret);
            if (Objects.isNull(loginUser)) {
                return "anonymous";
            }
            Sysuser sysuser = sysuserService.getSysuserByUuid(loginUser.getUuid());
            if (Objects.isNull(sysuser)) {
                return "admin:" + loginUser.getUuid();
            }
            return "admin:" + StringUtils.defaultIfBlank(sysuser.getUsername(), loginUser.getUuid());
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private String resolveAuthType(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StringUtils.isBlank(authorization)) {
            return "ANONYMOUS";
        }
        if (StringUtils.startsWithIgnoreCase(authorization, "Basic ")) {
            return "PUBLIC_API";
        }
        return "ADMIN";
    }

    private String getClientIp(HttpServletRequest request) {
        String[] headerNames = {
                "X-Forwarded-For",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR"
        };
        for (String headerName : headerNames) {
            String ip = request.getHeader(headerName);
            if (StringUtils.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
                return StringUtils.substringBefore(ip, ",").trim();
            }
        }
        return request.getRemoteAddr();
    }

    private String sanitizeRequestBody(String body) {
        return sanitizeTextContent(body);
    }

    private String sanitizeResponseBody(String uri, String body) {
        if (StringUtils.isBlank(body)) {
            return "";
        }
        if (StringUtils.endsWithIgnoreCase(uri, "/loginDo")
                || StringUtils.endsWithIgnoreCase(uri, "/getSystemLogs")) {
            return "[sensitive response omitted]";
        }
        return sanitizeTextContent(body);
    }

    private String sanitizeTextContent(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        String sanitizedText = text;
        try {
            Object parsed = JSON.parse(text);
            Object sanitizedObject = sanitizeJsonValue(parsed, null);
            sanitizedText = JSON.toJSONString(sanitizedObject);
        } catch (Exception ignored) {
            sanitizedText = maskSpecialTokens(text);
        }
        sanitizedText = maskSpecialTokens(sanitizedText);
        return StringUtils.abbreviate(sanitizedText, MAX_LOG_BODY_LENGTH);
    }

    private Object sanitizeJsonValue(Object value, String fieldName) {
        if (Objects.isNull(value)) {
            return null;
        }
        if (isSensitiveKey(fieldName)) {
            return "***";
        }
        if (value instanceof Map<?, ?>) {
            Map<?, ?> sourceMap = (Map<?, ?>) value;
            Map<String, Object> resultMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                resultMap.put(key, sanitizeJsonValue(entry.getValue(), key));
            }
            return resultMap;
        }
        if (value instanceof Collection<?>) {
            Collection<?> sourceCollection = (Collection<?>) value;
            List<Object> resultList = new ArrayList<>(sourceCollection.size());
            for (Object item : sourceCollection) {
                resultList.add(sanitizeJsonValue(item, fieldName));
            }
            return resultList;
        }
        if (value instanceof String) {
            return maskSpecialTokens((String) value);
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        return StringUtils.isNotBlank(key) && SENSITIVE_KEYS.contains(StringUtils.lowerCase(key));
    }

    private String maskSpecialTokens(String text) {
        if (StringUtils.isBlank(text)) {
            return text;
        }
        return JWT_PATTERN.matcher(text).replaceAll("***");
    }

    private String getResponseBody(ContentCachingResponseWrapper responseWrapper) {
        byte[] content = responseWrapper.getContentAsByteArray();
        if (content.length == 0) {
            return "";
        }
        Charset charset = resolveResponseCharset(responseWrapper);
        return new String(content, charset);
    }

    private Charset resolveResponseCharset(ContentCachingResponseWrapper responseWrapper) {
        String contentType = responseWrapper.getContentType();
        if (StringUtils.containsIgnoreCase(contentType, "application/json")
                || StringUtils.containsIgnoreCase(contentType, "+json")) {
            return StandardCharsets.UTF_8;
        }
        if (StringUtils.isNotBlank(responseWrapper.getCharacterEncoding())) {
            try {
                return Charset.forName(responseWrapper.getCharacterEncoding());
            } catch (Exception ignored) {
                return StandardCharsets.UTF_8;
            }
        }
        return StandardCharsets.UTF_8;
    }

    private Integer extractBusinessCode(String responseBody) {
        try {
            JSONObject jsonObject = JSONObject.parseObject(responseBody);
            return jsonObject.getInteger("code");
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractBusinessMessage(String responseBody) {
        try {
            JSONObject jsonObject = JSONObject.parseObject(responseBody);
            return jsonObject.getString("message");
        } catch (Exception ignored) {
            return "";
        }
    }

    private String buildExceptionMessage(Throwable throwable) {
        if (Objects.isNull(throwable)) {
            return "";
        }
        String exceptionMessage = throwable.getClass().getSimpleName();
        if (StringUtils.isNotBlank(throwable.getMessage())) {
            exceptionMessage = exceptionMessage + ": " + throwable.getMessage();
        }
        return StringUtils.abbreviate(exceptionMessage, 512);
    }

    private String resolveLogLevel(int httpStatus, Integer businessCode, Throwable throwable) {
        if (httpStatus >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR || Objects.nonNull(throwable)) {
            return "ERROR";
        }
        if (httpStatus >= HttpServletResponse.SC_BAD_REQUEST
                || (Objects.nonNull(businessCode) && businessCode.intValue() != ResponseResult.RespCode.OK.getCode())) {
            return "WARN";
        }
        return "INFO";
    }

    private boolean shouldSkipAuditLog(HttpServletRequest request) {
        return "GET".equalsIgnoreCase(request.getMethod())
                || StringUtils.endsWithIgnoreCase(request.getRequestURI(), "/loginDo");
    }

    private String buildAuditContent(HttpServletRequest request,
                                     String level,
                                     String businessMessage,
                                     Throwable throwable,
                                     String requestBody) {
        String method = StringUtils.upperCase(request.getMethod());
        String uri = request.getRequestURI();
        String action = resolveActionName(method, uri);
        String result = isSuccessLevel(level) ? "SUCCESS" : "FAIL";
        StringBuilder builder = new StringBuilder();
        builder.append(action).append(" ").append(uri).append(" ").append(result);
        if (!"GET".equals(method)) {
            String keySummary = extractKeySummary(request, requestBody);
            if (StringUtils.isNotBlank(keySummary)) {
                builder.append(", keys: ").append(keySummary);
            }
        }
        String reason = resolveAuditReason(businessMessage, throwable);
        if (!isSuccessLevel(level) && StringUtils.isNotBlank(reason)) {
            builder.append(", reason: ").append(reason);
        }
        return StringUtils.abbreviate(builder.toString(), MAX_AUDIT_CONTENT_LENGTH);
    }

    private String resolveActionName(String method, String uri) {
        String lowerUri = StringUtils.lowerCase(uri);
        if ("GET".equals(method)) {
            return "VIEW";
        }
        if ("DELETE".equals(method)) {
            return "DELETE";
        }
        if ("PUT".equals(method) || "PATCH".equals(method)) {
            return "UPDATE";
        }
        if ("POST".equals(method)) {
            if (containsAny(lowerUri, "add", "insert", "create", "register", "bind", "import", "sync")) {
                return "CREATE";
            }
            if (containsAny(lowerUri, "delete", "remove", "unbind", "release")) {
                return "DELETE";
            }
            if (containsAny(lowerUri, "update", "edit", "set", "change", "reset", "modify",
                    "enable", "disable", "start", "stop", "reboot", "shutdown")) {
                return "UPDATE";
            }
        }
        return "ACTION";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (StringUtils.contains(text, keyword)) {
                return true;
            }
        }
        return false;
    }

    private String extractKeySummary(HttpServletRequest request, String requestBody) {
        LinkedHashMap<String, String> summaryMap = new LinkedHashMap<>();
        collectParameterMap(summaryMap, request.getParameterMap());
        collectRequestBody(summaryMap, requestBody);
        appendPathTail(summaryMap, request.getRequestURI());
        if (summaryMap.isEmpty()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, String> entry : summaryMap.entrySet()) {
            values.add(entry.getKey() + "=" + entry.getValue());
        }
        return StringUtils.abbreviate(String.join(", ", values), MAX_AUDIT_CONTENT_LENGTH / 2);
    }

    private void collectParameterMap(Map<String, String> summaryMap, Map<String, String[]> parameterMap) {
        if (Objects.isNull(parameterMap) || parameterMap.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            if (summaryMap.size() >= MAX_AUDIT_FIELDS) {
                return;
            }
            String key = entry.getKey();
            if (!shouldCaptureAuditKey(key)) {
                continue;
            }
            String[] values = entry.getValue();
            if (Objects.isNull(values) || values.length == 0) {
                continue;
            }
            summaryMap.put(key, abbreviateAuditValue(String.join("|", values)));
        }
    }

    private void collectRequestBody(Map<String, String> summaryMap, String requestBody) {
        if (StringUtils.isBlank(requestBody) || summaryMap.size() >= MAX_AUDIT_FIELDS) {
            return;
        }
        try {
            Object parsed = JSON.parse(requestBody);
            collectJsonValue(summaryMap, parsed);
        } catch (Exception ignored) {
        }
    }

    private void collectJsonValue(Map<String, String> summaryMap, Object parsed) {
        if (!(parsed instanceof Map<?, ?>) || summaryMap.size() >= MAX_AUDIT_FIELDS) {
            return;
        }
        Map<?, ?> parsedMap = (Map<?, ?>) parsed;
        for (Map.Entry<?, ?> entry : parsedMap.entrySet()) {
            if (summaryMap.size() >= MAX_AUDIT_FIELDS) {
                return;
            }
            String key = String.valueOf(entry.getKey());
            if (!shouldCaptureAuditKey(key)) {
                continue;
            }
            summaryMap.put(key, abbreviateAuditValue(convertAuditValue(entry.getValue())));
        }
    }

    private String convertAuditValue(Object value) {
        if (Objects.isNull(value)) {
            return "null";
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return JSON.toJSONString(value);
    }

    private void appendPathTail(Map<String, String> summaryMap, String uri) {
        if (summaryMap.size() >= MAX_AUDIT_FIELDS || StringUtils.isBlank(uri)) {
            return;
        }
        String tail = StringUtils.substringAfterLast(uri, "/");
        if (StringUtils.isBlank(tail) || "admin".equalsIgnoreCase(tail)) {
            return;
        }
        if (StringUtils.isNumeric(tail) || tail.matches("[0-9a-fA-F\\-]{6,}")) {
            summaryMap.putIfAbsent("pathId", abbreviateAuditValue(tail));
        }
    }

    private boolean shouldCaptureAuditKey(String key) {
        if (StringUtils.isBlank(key) || isSensitiveKey(key)) {
            return false;
        }
        String lowerKey = StringUtils.lowerCase(key);
        for (String keyword : AUDIT_KEYWORDS) {
            if (StringUtils.contains(lowerKey, keyword)) {
                return true;
            }
        }
        return false;
    }

    private String abbreviateAuditValue(String value) {
        return StringUtils.abbreviate(maskSpecialTokens(StringUtils.defaultString(value)), MAX_AUDIT_VALUE_LENGTH);
    }

    private String resolveAuditReason(String businessMessage, Throwable throwable) {
        if (Objects.nonNull(throwable) && StringUtils.isNotBlank(throwable.getMessage())) {
            return StringUtils.abbreviate(throwable.getMessage(), 200);
        }
        return StringUtils.abbreviate(StringUtils.defaultString(businessMessage), 200);
    }

    private boolean isSuccessLevel(String level) {
        return "INFO".equals(level);
    }

    private void saveAccessLogToDatabase(String requestId,
                                         String level,
                                         HttpServletRequest request,
                                         String pathPattern,
                                         String handler,
                                         String clientIp,
                                         String operator,
                                         String authType,
                                         Integer httpStatus,
                                         Integer businessCode,
                                         Long durationMs,
                                         Throwable throwable,
                                         String content) {
        SystemLog systemLog = new SystemLog();
        systemLog.setRequestId(requestId);
        systemLog.setLogType("API");
        systemLog.setLevel(level);
        systemLog.setMethod(request.getMethod());
        systemLog.setUri(request.getRequestURI());
        systemLog.setPathPattern(pathPattern);
        systemLog.setHandler(handler);
        systemLog.setClientIp(clientIp);
        systemLog.setOperator(operator);
        systemLog.setAuthType(authType);
        systemLog.setHttpStatus(httpStatus);
        systemLog.setBusinessCode(businessCode);
        systemLog.setBusinessMessage(isSuccessLevel(level) ? "SUCCESS" : "FAIL");
        systemLog.setDurationMs(durationMs);
        systemLog.setException(buildExceptionMessage(throwable));
        systemLog.setContent(content);
        systemLog.setCreateTime(System.currentTimeMillis());
        systemLogService.saveSystemLogAsync(systemLog);
    }
}
