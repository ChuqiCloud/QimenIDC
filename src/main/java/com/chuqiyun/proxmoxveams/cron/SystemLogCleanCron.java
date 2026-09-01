package com.chuqiyun.proxmoxveams.cron;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chuqiyun.proxmoxveams.entity.Vmhost;
import com.chuqiyun.proxmoxveams.service.MasterService;
import com.chuqiyun.proxmoxveams.service.SystemLogService;
import com.chuqiyun.proxmoxveams.service.VmhostService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.HttpURLConnection;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author: 星禾
 * @Description: 系统日志清理及服务信息上报定时任务
 * @DateTime: 2026/6/7 10:49
 */
@Component
@EnableScheduling
public class SystemLogCleanCron {
    private static final String REPORT_URL = "https://auth.mryunqi.com/api/v1/report";
    private static final String REPORT_KEY = "AuthReport-9f4c7d2a6b1e8c0f";
    private static final String IPV4_PRIMARY_URL = "https://api.leapteam.cn/api/ip.php";
    private static final String IPV4_BACKUP_URL = "https://api.myip.la/";
    private static final long REPORT_INTERVAL = 60 * 60 * 1000L;
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "(?<!\\d)(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}(?!\\d)");

    @Resource
    private SystemLogService systemLogService;
    @Resource
    private RestTemplate restTemplate;
    @Resource
    private MasterService masterService;
    @Resource
    private VmhostService vmhostService;

    @Value("${info.application.build.version}")
    private String backendVersion;

    /**
     * @Author: 星禾
     * @Description: 每天凌晨清理过期系统日志
     * @DateTime: 2026/6/7 10:49
     */
    @Async
    @Scheduled(cron = "0 30 2 * * ?")
    public void cleanSystemLogCron() {
        systemLogService.deleteExpiredSystemLogs();
    }

    @Scheduled(initialDelay = 60 * 1000L, fixedRate = REPORT_INTERVAL)
    public void report() {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ipAddress", getLocalIpAddress());
            requestBody.put("nodeCount", masterService.count());
            requestBody.put("virtualMachineCount", vmhostService.count(activeVmQuery()));
            requestBody.put("backendVersion", backendVersion);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Report-Key", REPORT_KEY);
            restTemplate.postForObject(REPORT_URL, new HttpEntity<>(requestBody, headers), Void.class);
        } catch (Exception ignored) {
        }
    }

    private QueryWrapper<Vmhost> activeVmQuery() {
        QueryWrapper<Vmhost> queryWrapper = new QueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper.isNull("delete_state").or().eq("delete_state", 0));
        return queryWrapper;
    }

    private String getLocalIpAddress() {
        String publicIp = getPublicIpAddress(IPV4_PRIMARY_URL);
        if (publicIp != null) {
            return publicIp;
        }
        publicIp = getPublicIpAddress(IPV4_BACKUP_URL);
        if (publicIp != null) {
            return publicIp;
        }

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 80);
            InetAddress localAddress = socket.getLocalAddress();
            if (isUsableIpv4Address(localAddress)) {
                return localAddress.getHostAddress();
            }
        } catch (Exception ignored) {
        }

        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!isUsableInterface(networkInterface)) {
                    continue;
                }
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (isUsableIpv4Address(address)) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (SocketException ignored) {
        }
        return "";
    }

    private String getPublicIpAddress(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "text/plain");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            Matcher matcher = IPV4_PATTERN.matcher(response);
            return matcher.find() ? matcher.group() : null;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean isUsableInterface(NetworkInterface networkInterface) {
        try {
            return networkInterface != null
                    && networkInterface.isUp()
                    && !networkInterface.isLoopback()
                    && !networkInterface.isVirtual();
        } catch (SocketException ignored) {
            return false;
        }
    }

    private boolean isUsableIpv4Address(InetAddress address) {
        return address instanceof Inet4Address && !address.isLoopbackAddress() && !address.isLinkLocalAddress()
                && !address.isAnyLocalAddress();
    }
}
