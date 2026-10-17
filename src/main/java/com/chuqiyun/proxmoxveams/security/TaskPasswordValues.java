package com.chuqiyun.proxmoxveams.security;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 对任务参数中可能包含的密码值做递归保护。
 *
 * <p>任务参数的键来自多个历史任务实现，比较时忽略大小写、下划线和连字符，
 * 但不会改写普通键或普通值。已经带密文前缀的值保持不变，因此迁移脚本可安全重跑。</p>
 */
public final class TaskPasswordValues {
    private TaskPasswordValues() {
    }

    /**
     * 返回加密后的参数副本，不修改调用方持有的 Map。
     */
    public static Map<Object, Object> encrypt(Map<?, ?> params) {
        return transformMap(params, true);
    }

    /**
     * 为一次实体写入创建存储副本。业务层读出的明文即使以历史密文格式开头，
     * 也会再次生成新的随机密文；数据库迁移请使用 {@link #encrypt(Map)} 以保持幂等。
     */
    public static Map<Object, Object> encryptForStorage(Map<?, ?> params) {
        return transformMap(params, true, true);
    }

    /**
     * 返回解密后的参数副本，不修改调用方持有的 Map。
     */
    public static Map<Object, Object> decrypt(Map<?, ?> params) {
        return transformMap(params, false);
    }

    /**
     * 为接口响应创建脱敏副本，保留 stage/progress 等普通任务状态字段。
     * 该方法只删除敏感键，不修改业务对象中的明文参数。
     */
    public static Map<Object, Object> sanitizeForResponse(Map<?, ?> params) {
        return sanitizeMap(params);
    }

    static Object transform(Object value, boolean encrypt) {
        return transform(value, encrypt, false);
    }

    private static Object transform(Object value, boolean encrypt, boolean forceEncryption) {
        if (value instanceof Map<?, ?> map) {
            return transformMap(map, encrypt, forceEncryption);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>(collection.size());
            for (Object item : collection) {
                result.add(transform(item, encrypt, forceEncryption));
            }
            return result;
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> result = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                result.add(transform(Array.get(value, i), encrypt, forceEncryption));
            }
            return result;
        }
        return value;
    }

    private static Map<Object, Object> transformMap(Map<?, ?> source, boolean encrypt) {
        return transformMap(source, encrypt, false);
    }

    private static Map<Object, Object> transformMap(Map<?, ?> source, boolean encrypt, boolean forceEncryption) {
        Map<Object, Object> result = new LinkedHashMap<>();
        if (source == null) {
            return null;
        }
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (isPasswordKey(key)) {
                value = transformPasswordValue(value, encrypt, forceEncryption);
            } else {
                value = transform(value, encrypt, forceEncryption);
            }
            result.put(key, value);
        }
        return result;
    }

    private static Map<Object, Object> sanitizeMap(Map<?, ?> source) {
        if (source == null) {
            return null;
        }
        Map<Object, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (isSensitiveKey(entry.getKey())) {
                continue;
            }
            result.put(entry.getKey(), sanitizeValue(entry.getValue()));
        }
        return result;
    }

    private static Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return sanitizeMap(map);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>(collection.size());
            for (Object item : collection) {
                result.add(sanitizeValue(item));
            }
            return result;
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> result = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                result.add(sanitizeValue(Array.get(value, i)));
            }
            return result;
        }
        return value;
    }

    private static Object transformPasswordValue(Object value, boolean encrypt, boolean forceEncryption) {
        if (value == null) {
            return null;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>(collection.size());
            for (Object item : collection) {
                result.add(transformPasswordValue(item, encrypt, forceEncryption));
            }
            return result;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> result = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                result.add(transformPasswordValue(Array.get(value, i), encrypt, forceEncryption));
            }
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            return transformMap(map, encrypt, forceEncryption);
        }
        String text = value instanceof String ? (String) value : String.valueOf(value);
        if (encrypt) {
            if (!forceEncryption && StoredPasswordCipher.isEncrypted(text)) {
                // 迁移只跳过经过完整性校验的密文，坏密文不能被静默当作迁移成功。
                StoredPasswordCipher.decrypt(text);
                return text;
            }
            return StoredPasswordCipher.encrypt(text);
        }
        return StoredPasswordCipher.decrypt(text);
    }

    /**
     * 任务参数中涉及登录或系统凭据的字段。比较时统一为小写并移除分隔符。
     */
    public static boolean isPasswordKey(Object key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return normalized.endsWith("password") || normalized.endsWith("passwd") || normalized.endsWith("pwd");
    }

    public static boolean isSensitiveKey(Object key) {
        if (isPasswordKey(key)) {
            return true;
        }
        if (key == null) {
            return false;
        }
        String normalized = key.toString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return StoredLogRedactor.isSensitiveKey(key.toString())
                || normalized.equals("token")
                || normalized.endsWith("token")
                || normalized.equals("ticket")
                || normalized.endsWith("ticket")
                || normalized.equals("csrftoken")
                || normalized.equals("authorization")
                || normalized.equals("auth");
    }

}
