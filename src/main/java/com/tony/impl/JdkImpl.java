package com.tony.impl;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.tony.HttpExecutor;
import com.tony.Monitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JdkImpl implements HttpExecutor {

    private static final Logger log = LoggerFactory.getLogger(JdkImpl.class);

    private HttpClient httpClient;

    private ExecutorService executor;

    @Override
    public void init(Map<String, String> configs) throws Exception {
        int threads = Integer.parseInt(configs.getOrDefault("threads", "60"));
        int connectTimeout = Integer.parseInt(configs.getOrDefault("connectTimeout", "10000"));

        log.info("Jdk config: threads={} connTimeout={}", threads, connectTimeout);

        final TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                    }

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                    }

                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[]{};
                    }
                }
        };

        // Install the all-trusting trust manager
        final SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

        executor = Executors.newFixedThreadPool(threads, new ThreadFactoryBuilder().setNameFormat("http-pool-%d").build());
        httpClient = HttpClient.newBuilder()
                .sslContext(sslContext)
                // .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.of(connectTimeout, ChronoUnit.MILLIS))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .executor(executor)
                .build();
    }

    @Override
    public void close() throws Exception {
        executor.shutdown();
    }

    @Override
    public void asyncGet(String url, Monitor monitor) {
        long st = System.currentTimeMillis();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .GET()
                .build();

        try {
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .whenComplete((b, e) -> {
                        if (e == null) {
                            b.body();
                        }
                        monitor.finishOnce(System.currentTimeMillis() - st, e == null);
                    });
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public boolean doSyncGet(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .GET()
                .build();
        try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
