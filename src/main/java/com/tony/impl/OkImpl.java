package com.tony.impl;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.tony.HttpExecutor;
import com.tony.Monitor;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class OkImpl implements HttpExecutor {

    private static final Logger log = LoggerFactory.getLogger(OkImpl.class);

    private OkHttpClient httpClient;

    @Override
    public void init(Map<String, String> configs) throws Exception {
        int threads = Integer.parseInt(configs.getOrDefault("threads", "1000"));
        int maxConnection = Integer.parseInt(configs.getOrDefault("maxConnection", "2000"));
        int maxQueued = Integer.parseInt(configs.getOrDefault("maxQueued", "100000"));
        long connectTimeout = Long.parseLong(configs.getOrDefault("connectTimeout", "10000"));

        log.info("Ok config: threads={} maxConn={} maxQueue={} conTimeout={}",
                threads, maxConnection, maxQueued, connectTimeout);

        // 限制执行的线程
        Dispatcher dispatcher = new Dispatcher(Executors.newFixedThreadPool(
                threads, new ThreadFactoryBuilder().setNameFormat("my-pool-%d").build()));
        dispatcher.setMaxRequests(Integer.MAX_VALUE);
        dispatcher.setMaxRequestsPerHost(maxQueued);

        ConnectionPool connectionPool = new ConnectionPool(maxConnection, 60, TimeUnit.SECONDS);

        X509TrustManager x509TrustManager = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[]{};
            }
        };
        // Create a trust manager that does not validate certificate chains
        final TrustManager[] trustAllCerts = new TrustManager[] {x509TrustManager};

        // Install the all-trusting trust manager
        final SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

        // Create an ssl socket factory with our all-trusting manager
        final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(connectTimeout, TimeUnit.MILLISECONDS);
        builder.followRedirects(true);
        builder.dispatcher(dispatcher);
        builder.sslSocketFactory(sslSocketFactory, x509TrustManager);
        builder.connectionPool(connectionPool);

        httpClient = builder.build();
    }

    @Override
    public void close() throws Exception {

    }

    @Override
    public void asyncGet(String url, Monitor monitor) {
        long st = System.currentTimeMillis();

        Request request = new Request.Builder()
                .url(HttpUrl.get(url))
                .get().build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                // log.info("Request fail", e);
                // future.completeExceptionally(e);
                monitor.finishOnce(System.currentTimeMillis() - st, false);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                // log.info("current thread name is {}", Thread.currentThread().getName());
                response.body().string();
                response.close();
                monitor.finishOnce(System.currentTimeMillis() - st, true);
            }
        });
    }

    @Override
    public boolean doSyncGet(String url) {
        Request request = new Request.Builder()
                .url(HttpUrl.get(url))
                .get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            response.body().string();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
