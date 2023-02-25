package com.tony.jmh;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.dynamic.HttpClientTransportDynamic;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.util.HttpCookieStore;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;

@State(Scope.Benchmark)
public class JMH_Async_Jetty extends JMH_Async {

    private org.eclipse.jetty.client.HttpClient jettyClient;
    private ExecutorService executor;

    @Param({"2", "4"})
    public int ioThreads;

    @Setup
    @Override
    public void prepare(BasicState basicState) throws Exception {
        // Create a ClientConnector instance.
        ClientConnector connector = new ClientConnector();

        // Configure the ClientConnector.
        connector.setSelectors(Math.max(1, ioThreads));
        connector.setSslContextFactory(new SslContextFactory.Client(true));

        // Pass it to the HttpClient transport.
        HttpClientTransport transport = new HttpClientTransportDynamic(connector);
        jettyClient = new HttpClient(transport);
        jettyClient.setConnectTimeout(20000);
        jettyClient.setFollowRedirects(true);
        jettyClient.setCookieStore(new HttpCookieStore.Empty());
        jettyClient.setMaxConnectionsPerDestination(100000);
        jettyClient.setMaxRequestsQueuedPerDestination(100000);
        // httpClient.setScheduler();
        executor = Executors.newFixedThreadPool(80, new ThreadFactoryBuilder().setNameFormat("my-pool-%d").build());
        jettyClient.setExecutor(executor);

        jettyClient.start();

        // 先预热下，如果接口不够快，很容易各种溢出
        // for (int i = 0; i < 50; i++) {
        //     doRequest(null, null);
        // }
        // LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(5));
    }

    @TearDown
    public void stop() throws Exception {
        // System.out.print(" remove client");
        jettyClient.stop();
        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(2));
        executor.shutdownNow();
    }

    @Benchmark
    @Override
    public void throughput(OpCounters counters, BasicState basicState, Blackhole blackhole) throws Exception {
        LockSupport.parkNanos(TimeUnit.MICROSECONDS.toNanos(basicState.parkUs));
        doRequest(counters, basicState, blackhole);
    }

    // @Benchmark
    @Override
    public void avgTime(JMH_Async.OpCounters counters, BasicState basicState, Blackhole blackhole) throws Exception {
        LockSupport.parkNanos(TimeUnit.MICROSECONDS.toNanos(basicState.parkUs));
        doRequest(counters, basicState, blackhole);
    }


    private void doRequest(OpCounters counters, BasicState basicState, Blackhole blackhole) throws Exception {
        jettyClient.newRequest(basicState.url)
                .method(HttpMethod.GET)
                .timeout(2, TimeUnit.SECONDS)
                .send(new BufferingResponseListener() {

                    @Override
                    public void onComplete(Result result) {
                        if (result.isSucceeded()) {
                            if (null != blackhole) {
                                counters.incrSuccess();
                                blackhole.consume(getContentAsString());
                            }
                        } else {
                            // connection reset by peer
                            // Max requests queued per destination 1024 exceeded
                            // Throwable throwable = result.getFailure();
                            // if (null != throwable && null != throwable.getMessage() && !throwable.getMessage().contains("Max requests queued per destination") &&
                            //         !(throwable instanceof TimeoutException)) {
                            //     // System.out.println("msg is " + throwable.getMessage());
                            //     throwable.printStackTrace();
                            // }
                            if (null != blackhole) {
                                counters.incrFail();
                                blackhole.consume(result);
                            }
                        }
                    }
                });

    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(JMH_Async_Jetty.class.getSimpleName())
                .threads(1)
                .build();

        new Runner(opt).run();
    }



}
