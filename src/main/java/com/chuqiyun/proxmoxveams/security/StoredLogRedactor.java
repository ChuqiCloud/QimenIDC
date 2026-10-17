package com.chuqiyun.proxmoxveams.security;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * API 审计日志及历史日志共用的凭据字段脱敏规则。
 *
 * @author 星禾
 */
public final class StoredLogRedactor {
    public static final String INVALID_JSON_PLACEHOLDER = "<invalid JSON log body omitted>";
    public static final String INVALID_QUERY_PLACEHOLDER = "<invalid query log omitted>";
    public static final String SENSITIVE_TEXT_PLACEHOLDER = "<sensitive text log body omitted>";
    private static final String MASK = "***";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "pwd", "token", "authorization", "cookie", "secret", "appkey",
            "csrfpreventiontoken", "csrftoken", "ticket");
    private static final Pattern FIELD_ASSIGNMENT = Pattern.compile("[\\\"']?([a-zA-Z0-9_-]+)[\\\"']?\\s*[:=]");
    private static final Pattern TEXT_LOG_PREFIX = Pattern.compile("^\\[[a-zA-Z][a-zA-Z0-9 _-]*\\](?:\\s|$)");
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private StoredLogRedactor() {
    }

    /**
     * 未命中敏感字段的日志原样保留。截断、重复字段等异常 JSON 不保留原文，避免残留明文密码。
     * 旧版固定占位和普通文本仍可阅读；带凭据赋值的非 JSON 文本只保留固定占位。
     */
    public static String redact(String body) {
        if (body == null || body.isBlank()) {
            return body;
        }
        String trimmed = body.stripLeading();
        if ("[sensitive response omitted]".equals(trimmed)
                || INVALID_JSON_PLACEHOLDER.equals(trimmed)
                || SENSITIVE_TEXT_PLACEHOLDER.equals(trimmed)) {
            return body;
        }
        if (!looksLikeJson(trimmed)) {
            return containsSensitiveAssignment(body) ? SENSITIVE_TEXT_PLACEHOLDER : body;
        }
        try {
            JsonNode root = JSON_MAPPER.readTree(trimmed);
            return redactNode(root) ? JSON_MAPPER.writeValueAsString(root) : body;
        } catch (Exception exception) {
            return INVALID_JSON_PLACEHOLDER;
        }
    }

    public static boolean isSensitiveKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return SENSITIVE_KEYS.contains(normalized)
                || normalized.endsWith("password")
                || normalized.endsWith("passwd")
                || normalized.endsWith("pwd");
    }

    /** 仅解码查询参数名用于匹配；普通参数的编码、顺序和重复项保持原样。 */
    public static String redactQueryString(String queryString) {
        if (queryString == null || queryString.isBlank() || INVALID_QUERY_PLACEHOLDER.equals(queryString)) {
            return queryString;
        }
        String[] parts = queryString.split("&", -1);
        boolean changed = false;
        try {
            for (int index = 0; index < parts.length; index++) {
                String part = parts[index];
                int separator = part.indexOf('=');
                String rawKey = separator < 0 ? part : part.substring(0, separator);
                String key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
                if (separator >= 0) {
                    // 参数值保持原始编码，仅校验非法转义，避免异常输入绕过安全处置。
                    URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8);
                }
                if (isSensitiveKey(key) && separator >= 0) {
                    String redacted = rawKey + "=" + MASK;
                    changed |= !part.equals(redacted);
                    parts[index] = redacted;
                }
            }
            return changed ? String.join("&", parts) : queryString;
        } catch (IllegalArgumentException exception) {
            return INVALID_QUERY_PLACEHOLDER;
        }
    }

    private static boolean redactNode(JsonNode node) {
        if (node == null) {
            return false;
        }
        boolean changed = false;
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();
                if (isSensitiveKey(field.getKey()) && !value.isNull()) {
                    if (!value.isTextual() || !MASK.equals(value.textValue())) {
                        object.put(field.getKey(), MASK);
                        changed = true;
                    }
                } else {
                    changed |= redactNode(value);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                changed |= redactNode(item);
            }
        }
        return changed;
    }

    private static boolean looksLikeJson(String body) {
        char first = body.charAt(0);
        if (first == '{' || first == '"') {
            return true;
        }
        // 保留历史纯文本响应中的 [INFO]、[service] 等日志前缀。
        return first == '[' && !TEXT_LOG_PREFIX.matcher(body).find();
    }

    private static boolean containsSensitiveAssignment(String body) {
        Matcher matcher = FIELD_ASSIGNMENT.matcher(body);
        while (matcher.find()) {
            if (isSensitiveKey(matcher.group(1))) {
                return true;
            }
        }
        return false;
    }
}
