package com.chuqiyun.proxmoxveams.utils;

import javax.net.ssl.*;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * @author mryunqi
 * @date 2023/6/18
 */
public class TrustSslUtil {

    public static void initDefaultSsl() {
        try {
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(
                        java.security.cert.X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(
                        java.security.cert.X509Certificate[] certs, String authType) {
                }
            }};

            // Install the all-trusting trust manager
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            // Create an SSL socket factory with our all-trusting manager
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

            // Create a HostnameVerifier that allows all hostnames
            HostnameVerifier allHostsValid = (hostname, session) -> true;

            // Install the all-trusting host verifier
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
    }

    /*public static void initDefaultSsl() {
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            HostnameVerifier hv = (urlHostName, session) -> true;
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509ExtendedTrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[]
                                                               x509Certificates, String s) {
                        }


                        @Override
                        public void checkServerTrusted(X509Certificate[]
                                                               x509Certificates, String s) {
                        }


                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }


                        @Override
                        public void checkClientTrusted(X509Certificate[]
                                                               x509Certificates, String s, Socket socket) {
                        }


                        @Override
                        public void checkServerTrusted(X509Certificate[]
                                                               x509Certificates, String s, Socket socket) {
                        }


                        @Override
                        public void checkClientTrusted(X509Certificate[]
                                                               x509Certificates, String s, SSLEngine sslEngine) {
                        }


                        @Override
                        public void checkServerTrusted(X509Certificate[]
                                                               x509Certificates, String s, SSLEngine sslEngine) {
                        }
                    }};
            sc.init(null, trustAllCerts, new SecureRandom());


            SSLContext.setDefault(sc);
            HttpsURLConnection.setDefaultHostnameVerifier(hv);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }*/

    public static SSLSocketFactory getIgnoreSslSocketFactory() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, getTrustManagers(), new java.security.SecureRandom());
            return sslContext.getSocketFactory();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Failed to create SSL socket factory", e);
        }
    }

    public static HostnameVerifier getTrustAnyHostnameVerifier() {
        return (hostname, session) -> true;
    }

    public static X509TrustManager[] getTrustManagers() {
        return new X509TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
        };
    }

}
