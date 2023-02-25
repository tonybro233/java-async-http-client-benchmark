package com.tony.impl;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.tony.HttpExecutor;
import com.tony.Monitor;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.dynamic.HttpClientTransportDynamic;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.util.HttpCookieStore;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;

public class JettyImpl implements HttpExecutor {

    private static final Logger log = LoggerFactory.getLogger(JettyImpl.class);

    private HttpClient httpClient;

    private ExecutorService executor;

    @Override
    public void init(Map<String, String> configs) throws Exception {
        int threads = Integer.parseInt(configs.getOrDefault("threads",
                Integer.toString(Runtime.getRuntime().availableProcessors() * 2)));
        int selectorThreads = Integer.parseInt(configs.getOrDefault("ioThreads",
                Integer.toString(Runtime.getRuntime().availableProcessors() / 2)));
        int maxConnection = Integer.parseInt(configs.getOrDefault("maxConnection", "1000"));
        int maxQueued = Integer.parseInt(configs.getOrDefault("maxQueued", "1000000"));
        long connectTimeout = Long.parseLong(configs.getOrDefault("connectTimeout", "20000"));

        log.info("Jetty config: threads={} selector={} maxConn={} maxQueue={} conTimeout={}",
                threads, selectorThreads, maxConnection, maxQueued, connectTimeout);

        ClientConnector connector = new ClientConnector();

        // Configure the ClientConnector.
        connector.setSelectors(Math.max(1, selectorThreads));
        connector.setSslContextFactory(new SslContextFactory.Client(true));

        // Pass it to the HttpClient transport.
        HttpClientTransport transport = new HttpClientTransportDynamic(connector);
        httpClient = new HttpClient(transport);
        httpClient.setConnectTimeout(connectTimeout);
        httpClient.setFollowRedirects(true);
        httpClient.setCookieStore(new HttpCookieStore.Empty());
        httpClient.setMaxConnectionsPerDestination(maxConnection);
        httpClient.setMaxRequestsQueuedPerDestination(maxQueued);
        // httpClient.setScheduler();
        executor = Executors.newFixedThreadPool(threads, new ThreadFactoryBuilder().setNameFormat("my-pool-%d").build());
        httpClient.setExecutor(executor);

        httpClient.start();
    }

    @Override
    public void close() throws Exception {
        httpClient.stop();
        executor.shutdown();
    }

    @Override
    public void asyncGet(String url, Monitor monitor) {
        long st = System.currentTimeMillis();
        httpClient.newRequest(url)
                .method(HttpMethod.GET)
                // .timeout(2, TimeUnit.SECONDS)
                .send(new BufferingResponseListener() {

                    @Override
                    public void onComplete(Result result) {
                        monitor.finishOnce(System.currentTimeMillis() - st, result.isSucceeded());
                        if (result.isSucceeded()) {
                            String string = getContentAsString();
                        } else {
                            // connection reset by peer
                            // Max requests queued per destination 1024 exceeded
                            Throwable throwable = result.getFailure();
                            if (!throwable.getMessage().contains("Max requests queued per destination") &&
                                    !(throwable instanceof TimeoutException)) {
                                // System.out.println("msg is " + throwable.getMessage());
                                // throwable.printStackTrace();
                            }
                        }
                    }
                });
    }

    @Override
    public boolean doSyncGet(String url) {
        try {
            httpClient.newRequest(url)
                    .method(HttpMethod.GET)
                    .send()
                    .getContentAsString();
            return true;
        } catch (Exception e) {
            // e.printStackTrace();
            return false;
        }
    }

}
