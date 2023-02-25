package com.tony.impl;

import com.tony.HttpExecutor;
import com.tony.Monitor;
import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.asynchttpclient.Dsl.asyncHttpClient;
import static org.asynchttpclient.Dsl.config;

public class AhcImpl implements HttpExecutor {

    private static final Logger log = LoggerFactory.getLogger(AhcImpl.class);

    private AsyncHttpClient httpClient;

    @Override
    public void init(Map<String, String> configs) throws Exception {
        int maxConnection = Integer.parseInt(configs.getOrDefault("maxConnection", "1000"));
        int ioThreads = Integer.parseInt(configs.getOrDefault("ioThreads", Integer.toString(Runtime.getRuntime().availableProcessors())));
        int connectTimeout = Integer.parseInt(configs.getOrDefault("connectTimeout", "20000"));

        log.info("Ahc config: ioThreads={} maxConnection={} connectTimeout={}",
                ioThreads, maxConnection, connectTimeout);

        httpClient = asyncHttpClient(config()
                // .setSslContext(SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build())
                .setUseInsecureTrustManager(true)
                .setFollowRedirect(true)
                .setKeepAlive(true)
                .setMaxConnections(maxConnection)
                .setThreadPoolName("custom_name")
                .setIoThreadsCount(ioThreads)
                .setConnectTimeout(connectTimeout));
    }

    @Override
    public void close() throws Exception {
        httpClient.close();
    }

    @Override
    public void asyncGet(String url, Monitor monitor) {
        long st = System.currentTimeMillis();
        httpClient.prepareGet(url)
                .execute(new AsyncCompletionHandler<>() {

                    @Override
                    public void onThrowable(Throwable t) {
                        t.printStackTrace();
                        monitor.finishOnce(System.currentTimeMillis() - st, false);
                    }

                    @Override
                    public Object onCompleted(Response response) throws Exception {
                        String resp = response.getResponseBody();
                        monitor.finishOnce(System.currentTimeMillis() - st, true);
                        return resp;
                    }
                });
    }

    @Override
    public boolean doSyncGet(String url) {
        try {
            httpClient.prepareGet(url).execute().get();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
