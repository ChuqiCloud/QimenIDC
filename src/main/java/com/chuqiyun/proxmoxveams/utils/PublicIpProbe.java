package com.chuqiyun.proxmoxveams.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 主控出口 IPv4 探测。
 * <p>
 * 多个公共探测源里没有一个是永远可用的：机房线路可能连不上 ipify，Cloudflare 也可能被墙。
 * 因此四个源全部并发请求，谁回来算谁的，结果合并去重——主控部署在容器或云 NAT 后面时，
 * 不同源甚至可能看到不同的出口地址，合并比「取第一个成功的」更接近真实情况。
 * <p>
 * 单源超时 {@link #PER_SOURCE_TIMEOUT_MILLIS}，整体最多等 {@link #TIMEOUT_MILLIS}，
 * 失败只记录原因不抛异常。
 *
 * @author CodeBuddy
 * @date 2026/10/17
 */
public final class PublicIpProbe {

    public static final int PER_SOURCE_TIMEOUT_MILLIS = 3_000;

    public static final int TIMEOUT_MILLIS = 5_000;

    private PublicIpProbe() {
    }

    /**
     * 一个探测源，format 决定怎么从响应体里抠出地址
     */
    public enum Source {
        CLOUDFLARE("cloudflare", "https://www.cloudflare.com/cdn-cgi/trace", Format.TRACE),
        IPIFY("ipify", "https://api.ipify.org/", Format.TEXT),
        IP_SB("ip.sb", "https://api.ip.sb/ip", Format.TEXT),
        IPIP("ipip", "https://myip.ipip.net/", Format.TEXT);

        private final String label;
        private final String url;
        private final Format format;

        Source(String label, String url, Format format) {
            this.label = label;
            this.url = url;
            this.format = format;
        }

        public String getLabel() {
            return label;
        }

        public String getUrl() {
            return url;
        }

        public Format getParser() {
            return format;
        }
    }

    public enum Format {
        /**
         * 纯文本，整个响应体就是一个地址
         */
        TEXT,
        /**
         * cloudflare cdn-cgi/trace，键值行，取 ip=
         */
        TRACE
    }

    /**
     * 并发探测全部源并合并结果
     *
     * @param fetch 取回文本的通道，便于测试替换
     */
    public static Result probe(TextFetcher fetch) {
        ExecutorService pool = Executors.newFixedThreadPool(Source.values().length, runnable -> {
            Thread thread = new Thread(runnable, "public-ip-probe");
            thread.setDaemon(true);
            return thread;
        });
        Map<String, String> addresses = new LinkedHashMap<>();
        Map<String, String> errors = new LinkedHashMap<>();
        Map<Source, Future<String>> pending = new LinkedHashMap<>();
        try {
            for (Source source : Source.values()) {
                Callable<String> task = () -> fetch.fetch(source);
                pending.put(source, pool.submit(task));
            }
            for (Map.Entry<Source, Future<String>> entry : pending.entrySet()) {
                Source source = entry.getKey();
                try {
                    String fetched = entry.getValue().get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                    String address = parse(source, fetched);
                    if (address.isEmpty()) {
                        errors.put(source.getLabel(), "unrecognized response");
                    } else {
                        addresses.put(source.getLabel(), address);
                    }
                } catch (Exception e) {
                    errors.put(source.getLabel(), describe(e));
                }
            }
        } finally {
            for (Future<String> future : pending.values()) {
                future.cancel(true);
            }
            pool.shutdownNow();
        }
        return new Result(addresses, errors);
    }

    /**
     * 按源的格式取出地址
     */
    public static String parse(Source source, String body) {
        if (body == null) {
            return "";
        }
        if (source.getParser() == Format.TRACE) {
            return parseTrace(body);
        }
        return sanitize(body);
    }

    /**
     * cloudflare trace：每行 key=value，取 ip=
     */
    public static String parseTrace(String body) {
        for (String line : body.split("\\R")) {
            int index = line.indexOf('=');
            if (index <= 0 || !"ip".equals(line.substring(0, index).trim())) {
                continue;
            }
            String value = normalize(line.substring(index + 1).trim());
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    /**
     * 从任意文本里取出第一个合法 IPv4。
     * <p>
     * ipip.net 返回的是「当前 IP：159.75.12.57  来自于：中国 广东 广州  电信」这种中文串，
     * ipify 也可能因为参数或风控返回 JSON、HTML，所以先按空白切词精确匹配，再对 JSON 取字段，
     * 最后用带边界断言的扫描兜底。边界断言保证 MAC 地址（00:1a:2b:3c:4d:5e）和
     * 形如 1.2.3.4.5 的版本号不会被误判成 IP。
     */
    public static String sanitize(String body) {
        if (body == null) {
            return "";
        }
        String text = body.replace("\uFEFF", "").trim();
        if (text.isEmpty() || text.length() > 1024) {
            return "";
        }
        for (String token : text.split("\\s+")) {
            String address = normalize(token);
            if (!address.isEmpty()) {
                return address;
            }
        }
        if (text.startsWith("{")) {
            String fromJson = fromJsonObject(text);
            if (!fromJson.isEmpty()) {
                return fromJson;
            }
        }
        return scan(text);
    }

    private static String fromJsonObject(String text) {
        for (String key : new String[]{"\"ip\"", "\"query\"", "\"origin\"", "\"address\""}) {
            int keyIndex = text.indexOf(key);
            if (keyIndex < 0) {
                continue;
            }
            int colon = text.indexOf(':', keyIndex + key.length());
            int quoteStart = colon < 0 ? -1 : text.indexOf('"', colon);
            int quoteEnd = quoteStart < 0 ? -1 : text.indexOf('"', quoteStart + 1);
            if (quoteEnd <= quoteStart) {
                continue;
            }
            String address = normalize(text.substring(quoteStart + 1, quoteEnd));
            if (!address.isEmpty()) {
                return address;
            }
        }
        return "";
    }

    /**
     * 扫描出所有「四段数字点分」的候选，前后不能紧邻数字或点，逐个校验
     */
    private static String scan(String text) {
        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current < '0' || current > '9') {
                index++;
                continue;
            }
            if (index > 0 && isBoundaryViolation(text.charAt(index - 1))) {
                index++;
                continue;
            }
            int end = index;
            while (end < text.length()) {
                char c = text.charAt(end);
                if ((c >= '0' && c <= '9') || c == '.') {
                    end++;
                    continue;
                }
                break;
            }
            if (end < text.length() && isBoundaryViolation(text.charAt(end))) {
                index = end + 1;
                continue;
            }
            String address = normalize(text.substring(index, end));
            if (!address.isEmpty()) {
                return address;
            }
            index = Math.max(end, index + 1);
        }
        return "";
    }

    private static boolean isBoundaryViolation(char neighbor) {
        return (neighbor >= '0' && neighbor <= '9') || neighbor == '.';
    }

    /**
     * 单个 token 转成规范 IPv4 字面量，非法返回空串
     */
    private static String normalize(String token) {
        String candidate = token.trim();
        while (!candidate.isEmpty() && ".,;:()[]<>\"'".indexOf(candidate.charAt(candidate.length() - 1)) >= 0) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        while (!candidate.isEmpty() && "\"'()[]<>".indexOf(candidate.charAt(0)) >= 0) {
            candidate = candidate.substring(1);
        }
        if (candidate.isEmpty() || candidate.length() > 15 || candidate.indexOf(':') >= 0) {
            return "";
        }
        int dots = 0;
        for (int i = 0; i < candidate.length(); i++) {
            char current = candidate.charAt(i);
            if (current == '.') {
                dots++;
            } else if (current < '0' || current > '9') {
                return "";
            }
        }
        if (dots != 3) {
            return "";
        }
        String[] parts = candidate.split("\\.", -1);
        if (parts.length != 4) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return "";
            }
            if (part.length() > 1 && part.charAt(0) == '0') {
                return "";
            }
            int value = Integer.parseInt(part);
            if (value > 255) {
                return "";
            }
            if (builder.length() > 0) {
                builder.append('.');
            }
            builder.append(value);
        }
        String address = builder.toString();
        // 这些地址不可能是出口地址，拿到说明线路在拦截，宁可当作失败
        if (address.startsWith("0.") || address.startsWith("127.") || address.startsWith("169.254.")
                || address.startsWith("224.") || address.startsWith("255.")) {
            return "";
        }
        return address;
    }

    private static String describe(Exception error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        if (cause instanceof java.util.concurrent.TimeoutException) {
            return "timeout";
        }
        String message = cause.getMessage();
        if (message == null || message.isEmpty()) {
            message = cause.getClass().getSimpleName();
        }
        return message.length() > 80 ? message.substring(0, 80) : message;
    }

    /**
     * 取回探测源响应体的通道
     */
    public interface TextFetcher {
        String fetch(Source source) throws Exception;
    }

    /**
     * 探测结果：各源报告的地址 + 失败原因
     */
    public static final class Result {
        private final Map<String, String> addresses;
        private final Map<String, String> errors;

        Result(Map<String, String> addresses, Map<String, String> errors) {
            this.addresses = addresses;
            this.errors = errors;
        }

        /**
         * 合并去重后的地址，可能为空
         */
        public List<String> getAddresses() {
            List<String> result = new ArrayList<>();
            for (String address : addresses.values()) {
                if (!result.contains(address)) {
                    result.add(address);
                }
            }
            return result;
        }

        /**
         * 源名 -> 地址
         */
        public Map<String, String> getSources() {
            return new LinkedHashMap<>(addresses);
        }

        /**
         * 源名 -> 失败原因
         */
        public Map<String, String> getErrors() {
            return new LinkedHashMap<>(errors);
        }

        public boolean isEmpty() {
            return addresses.isEmpty();
        }
    }
}
