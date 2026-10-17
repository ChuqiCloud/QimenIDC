package com.chuqiyun.proxmoxveams.config;

import com.chuqiyun.proxmoxveams.utils.PublicIpProbe;
import com.chuqiyun.proxmoxveams.utils.CertificateUtil;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.client.config.RequestConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.springframework.http.client.ClientHttpRequestFactory;

import javax.net.ssl.SSLContext;
import java.security.KeyStoreException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * @author mryunqi
 * @date 2023/12/6
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(ClientHttpRequestFactory factory){
        return new RestTemplate(factory);
    }

    @Bean
    public ClientHttpRequestFactory simpleClientHttpRequestFactory(){
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(15000);
        factory.setReadTimeout(5000);
        return factory;
    }

    public static HttpComponentsClientHttpRequestFactory generateHttpRequestFactory()
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException
    {
        TrustStrategy acceptingTrustStrategy = (x509Certificates, authType) -> true;
        SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
        SSLConnectionSocketFactory connectionSocketFactory = new SSLConnectionSocketFactory(sslContext, new NoopHostnameVerifier());

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(15_000)
                .setConnectionRequestTimeout(10_000)
                .setSocketTimeout(30_000)
                .build();
        HttpClientBuilder httpClientBuilder = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries();
        httpClientBuilder.setSSLSocketFactory(connectionSocketFactory);
        CloseableHttpClient httpClient = httpClientBuilder.build();
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setHttpClient(httpClient);
        return factory;
    }

    /**
     * Creates the same bounded-timeout client for utility classes that cannot use
     * Spring's injected RestTemplate bean.
     */
    public static RestTemplate createRestTemplate() {
        try {
            return new RestTemplate(generateHttpRequestFactory());
        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            throw new IllegalStateException("创建HTTP客户端失败", e);
        }
    }

    /**
     * 公共 IP 探测专用：目标是正规 CA 证书的公共站点，按系统默认信任链校验，
     * 超时压到 3 秒，避免探测源卡住拖慢启动。
     */
    public static RestTemplate createProbeRestTemplate() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(PublicIpProbe.PER_SOURCE_TIMEOUT_MILLIS)
                .setConnectionRequestTimeout(PublicIpProbe.PER_SOURCE_TIMEOUT_MILLIS)
                .setSocketTimeout(PublicIpProbe.PER_SOURCE_TIMEOUT_MILLIS)
                .build();
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .setSSLSocketFactory(new SSLConnectionSocketFactory(
                        SSLContexts.createSystemDefault(),
                        SSLConnectionSocketFactory.getDefaultHostnameVerifier()))
                .build();
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectTimeout(PublicIpProbe.PER_SOURCE_TIMEOUT_MILLIS);
        factory.setReadTimeout(PublicIpProbe.PER_SOURCE_TIMEOUT_MILLIS);
        return new RestTemplate(factory);
    }

    public static RestTemplate createQAgentRestTemplate() {
        return createQAgentRestTemplate(30_000, null);
    }

    /**
     * 为单个被控节点创建 HTTPS 客户端。节点证书是自签证书时，
     * 只信任主控保存的那张证书，并继续执行地址校验。
     */
    public static RestTemplate createQAgentRestTemplate(String nodePublicKey) {
        return createQAgentRestTemplate(30_000, nodePublicKey);
    }

    /**
     * 迁移阶段接口是同步执行后返回，vzdump/qmrestore 可能跑很久，单独放宽超时。
     */
    public static RestTemplate createQAgentRestTemplate(int readTimeoutMillis) {
        return createQAgentRestTemplate(readTimeoutMillis, null);
    }

    public static RestTemplate createQAgentRestTemplate(int readTimeoutMillis, String nodePublicKey) {
        int socketTimeout = Math.max(1_000, readTimeoutMillis);
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(15_000)
                .setConnectionRequestTimeout(10_000)
                .setSocketTimeout(socketTimeout)
                .build();
        try {
            SSLContext sslContext = createQAgentSslContext(nodePublicKey);
            CloseableHttpClient httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .disableAutomaticRetries()
                    .setSSLSocketFactory(new SSLConnectionSocketFactory(
                            sslContext,
                            SSLConnectionSocketFactory.getDefaultHostnameVerifier()))
                    .build();
            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
            return new RestTemplate(factory);
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            throw new IllegalStateException("创建QAgent HTTPS客户端失败", e);
        }
    }

    private static SSLContext createQAgentSslContext(String nodePublicKey)
            throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        if (nodePublicKey == null || nodePublicKey.trim().isEmpty()) {
            return SSLContexts.createSystemDefault();
        }
        X509Certificate pinned = CertificateUtil.parse(nodePublicKey);
        TrustStrategy trustStrategy = (chain, authType) -> {
            if (chain == null || chain.length == 0 || !chain[0].equals(pinned)) {
                throw new CertificateException("QAgent证书与节点保存的证书不一致");
            }
            chain[0].checkValidity();
            return true;
        };
        return SSLContexts.custom().loadTrustMaterial(null, trustStrategy).build();
    }
}
