package com.chuqiyun.proxmoxveams.filter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chuqiyun.proxmoxveams.common.ResponseResult;
import com.chuqiyun.proxmoxveams.common.UnifiedLogger;
import com.chuqiyun.proxmoxveams.entity.SystemLog;
import com.chuqiyun.proxmoxveams.entity.Sysuser;
import com.chuqiyun.proxmoxveams.service.SystemLogService;
import com.chuqiyun.proxmoxveams.service.SysuserService;
import com.chuqiyun.proxmoxveams.utils.ServletUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.servlet.HandlerMapping;

import javax.annotation.Resource;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * @author mryunqi
 * @date 2023/8/10
 */
@Slf4j
@Component
public class ReplaceRequestBodyFilter extends OncePerRequestFilter {
    private static final int MAX_LOG_BODY_LENGTH = 4096;
    private static final Pattern JWT_PATTERN = Pattern.compile("\\b[A-Za-z0-9\\-_]+=*\\.[A-Za-z0-9\\-_]+=*\\.[A-Za-z0-9\\-_]+=*\\b");
    private static final Set<String> SENSITIVE_KEYS = new HashSet<>(Arrays.asList(
            "password", "passwd", "pwd", "token", "authorization", "cookie",
            "secret", "appkey", "csrfpreventiontoken", "ticket"
    ));

    @Resource
    private SysuserService sysuserService;

    @Resource
    private SystemLogService systemLogService;

    @org.springframework.beans.factory.annotation.Value("${config.secret}")
    private String secret;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
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
            } catch (Exception logException) {
                log.warn("记录接口访问日志失败: {}", logException.getMessage(), logException);
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
        String query = sanitizeQueryString(request.getQueryString());

        Map<String, Object> accessLog = new LinkedHashMap<>();
        accessLog.put("event", "http_access");
        accessLog.put("requestId", requestId);
        accessLog.put("method", request.getMethod());
        accessLog.put("uri", request.getRequestURI());
        if (StringUtils.isNotBlank(pathPattern)) {
            accessLog.put("pathPattern", pathPattern);
        }
        if (StringUtils.isNotBlank(handler)) {
            accessLog.put("handler", handler);
        }
        accessLog.put("clientIp", clientIp);
        accessLog.put("operator", operator);
        accessLog.put("authType", authType);
        if (StringUtils.isNotBlank(query)) {
            accessLog.put("query", query);
        }
        if (StringUtils.isNotBlank(requestBody)) {
            accessLog.put("requestBody", requestBody);
        }
        accessLog.put("httpStatus", responseWrapper.getStatus());
        if (Objects.nonNull(businessCode)) {
            accessLog.put("businessCode", businessCode);
        }
        if (StringUtils.isNotBlank(businessMessage)) {
            accessLog.put("businessMessage", businessMessage);
        }
        if (StringUtils.isNotBlank(responseBody)) {
            accessLog.put("responseBody", responseBody);
        }
        accessLog.put("durationMs", durationMs);
        if (Objects.nonNull(throwable)) {
            accessLog.put("exception", buildExceptionMessage(throwable));
        }

        String accessLogMessage = JSON.toJSONString(accessLog);
        String level = resolveLogLevel(responseWrapper.getStatus(), businessCode, throwable);
        if ("ERROR".equals(level)) {
            UnifiedLogger.error(UnifiedLogger.LogType.API, "{}", accessLogMessage);
        } else if ("WARN".equals(level)) {
            UnifiedLogger.warn(UnifiedLogger.LogType.API, "{}", accessLogMessage);
        } else {
            UnifiedLogger.log(UnifiedLogger.LogType.API, "{}", accessLogMessage);
        }
        saveAccessLogToDatabase(requestId, level, request, pathPattern, handler, clientIp, operator, authType,
                query, requestBody, responseWrapper.getStatus(), businessCode, businessMessage, responseBody,
                durationMs, throwable, accessLogMessage);
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
                String decoded = new String(Base64.getDecoder().decode(StringUtils.substringAfter(authorization, "Basic ").trim()), StandardCharsets.UTF_8);
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

    private String sanitizeQueryString(String queryString) {
        if (StringUtils.isBlank(queryString)) {
            return "";
        }
        List<String> result = new ArrayList<>();
        String[] queryItems = StringUtils.split(queryString, "&");
        if (Objects.isNull(queryItems)) {
            return "";
        }
        for (String item : queryItems) {
            String key = StringUtils.substringBefore(item, "=");
            String value = StringUtils.substringAfter(item, "=");
            if (isSensitiveKey(key)) {
                result.add(key + "=***");
            } else if (StringUtils.contains(item, "=")) {
                result.add(key + "=" + maskSpecialTokens(value));
            } else {
                result.add(maskSpecialTokens(item));
            }
        }
        return StringUtils.abbreviate(String.join("&", result), MAX_LOG_BODY_LENGTH);
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
        Charset charset = StandardCharsets.UTF_8;
        if (StringUtils.isNotBlank(responseWrapper.getCharacterEncoding())) {
            try {
                charset = Charset.forName(responseWrapper.getCharacterEncoding());
            } catch (Exception ignored) {
                charset = StandardCharsets.UTF_8;
            }
        }
        return new String(content, charset);
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

    private void saveAccessLogToDatabase(String requestId,
                                         String level,
                                         HttpServletRequest request,
                                         String pathPattern,
                                         String handler,
                                         String clientIp,
                                         String operator,
                                         String authType,
                                         String query,
                                         String requestBody,
                                         Integer httpStatus,
                                         Integer businessCode,
                                         String businessMessage,
                                         String responseBody,
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
        systemLog.setQueryString(query);
        systemLog.setRequestBody(requestBody);
        systemLog.setHttpStatus(httpStatus);
        systemLog.setBusinessCode(businessCode);
        systemLog.setBusinessMessage(businessMessage);
        systemLog.setResponseBody(responseBody);
        systemLog.setDurationMs(durationMs);
        systemLog.setException(buildExceptionMessage(throwable));
        systemLog.setContent(content);
        systemLog.setCreateTime(System.currentTimeMillis());
        systemLogService.saveSystemLogAsync(systemLog);
    }
}

