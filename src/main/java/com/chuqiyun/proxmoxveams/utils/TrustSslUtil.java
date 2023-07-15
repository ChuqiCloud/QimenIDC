package com.chuqiyun.proxmoxveams.utils;

import javax.net.ssl.*;
import java.net.Socket;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * @author mryunqi
 * @date 2023/6/18
 */
public class TrustSslUtil {

    public static void initDefaultSsl() {
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
    }

}
