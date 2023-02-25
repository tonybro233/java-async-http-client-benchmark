package com.tony.impl;

import com.tony.HttpExecutor;
import com.tony.Monitor;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ApacheSyncImpl implements HttpExecutor {


    private CloseableHttpClient httpClient;


    @Override
    public void init(Map<String, String> configs) throws Exception {
        Integer maxConnection = Integer.parseInt(configs.getOrDefault("maxConnection", "1000"));
        Integer connectTimeout = Integer.parseInt(configs.getOrDefault("connectTimeout", "10000"));
        Integer responseTimeout = Integer.parseInt(configs.getOrDefault("responseTimeout", "10000"));

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                .setResponseTimeout(responseTimeout, TimeUnit.MILLISECONDS)
                .build();

        httpClient = HttpClientBuilder.create()
                .disableCookieManagement()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(Integer.MAX_VALUE) // default 25
                        .setMaxConnPerRoute(maxConnection) // default 5
                        .build())
                .build();
    }

    @Override
    public void close() throws Exception {
        httpClient.close();
    }

    @Override
    public void asyncGet(String url, Monitor monitor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean doSyncGet(String url) {
        try {
            String res = httpClient.execute(new HttpGet(url), new BasicHttpClientResponseHandler());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
