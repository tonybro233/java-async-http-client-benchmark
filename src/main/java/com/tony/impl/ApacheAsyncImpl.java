package com.tony.impl;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.tony.HttpExecutor;
import com.tony.Monitor;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ApacheAsyncImpl implements HttpExecutor {

    private static final Logger log = LoggerFactory.getLogger(ApacheAsyncImpl.class);

    private CloseableHttpAsyncClient httpClient;

    @Override
    public void init(Map<String, String> configs) throws Exception {
        int ioThreads = Integer.parseInt(configs.getOrDefault("ioThreads", Integer.toString(Runtime.getRuntime().availableProcessors())));
        int maxConnection = Integer.parseInt(configs.getOrDefault("maxConnection", "2000"));
        int connectTimeout = Integer.parseInt(configs.getOrDefault("connectTimeout", "10000"));
        int responseTimeout = Integer.parseInt(configs.getOrDefault("responseTimeout", "10000"));

        log.info("Apache async config: ioThreads={} maxConn={} conTimeout={} respTimeout={}",
                ioThreads, maxConnection, connectTimeout, responseTimeout);

        IOReactorConfig reactorConfig = IOReactorConfig.custom()
                .setIoThreadCount(ioThreads)
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                .setResponseTimeout(responseTimeout, TimeUnit.MILLISECONDS)
                .build();

        httpClient = HttpAsyncClientBuilder.create()
                .useSystemProperties()
                .setIOReactorConfig(reactorConfig)
                .disableCookieManagement()
                .setThreadFactory(new ThreadFactoryBuilder().setNameFormat("my-pool-%d").build())
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(Integer.MAX_VALUE) // default 25
                        .setMaxConnPerRoute(maxConnection) // default 5
                        .build())
                .build();

        httpClient.start();
    }

    @Override
    public void close() throws Exception {
        httpClient.close();
    }

    @Override
    public void asyncGet(String url, Monitor monitor) {
        long st = System.currentTimeMillis();
        httpClient.execute(SimpleRequestBuilder.get(url).build(), new FutureCallback<SimpleHttpResponse>() {
            @Override
            public void completed(SimpleHttpResponse response) {
                response.getBody().getBodyText();
                monitor.finishOnce(System.currentTimeMillis() - st, true);
            }

            @Override
            public void failed(Exception ex) {
                // log.error("request error", ex);
                monitor.finishOnce(System.currentTimeMillis() - st, false);
            }

            @Override
            public void cancelled() {
                // log.info("request canceled");
                monitor.finishOnce(System.currentTimeMillis() - st, false);
            }
        });
    }

    @Override
    public boolean doSyncGet(String url) {
        throw new UnsupportedOperationException();
    }

}
