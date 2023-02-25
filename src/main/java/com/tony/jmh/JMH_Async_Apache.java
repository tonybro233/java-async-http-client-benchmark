package com.tony.jmh;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

@State(Scope.Benchmark)
public class JMH_Async_Apache extends JMH_Async {

    private CloseableHttpAsyncClient httpClient;

    @Param({"2", "4"})
    public int ioThreads;

    @Override
    @Setup
    public void prepare(BasicState basicState) throws Exception {
        IOReactorConfig reactorConfig = IOReactorConfig.custom()
                .setIoThreadCount(ioThreads)
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(10_000, TimeUnit.MILLISECONDS)
                .setResponseTimeout(10_000, TimeUnit.MILLISECONDS)
                .build();

        httpClient = HttpAsyncClientBuilder.create()
                .useSystemProperties()
                .setIOReactorConfig(reactorConfig)
                .disableCookieManagement()
                .setThreadFactory(new ThreadFactoryBuilder().setNameFormat("my-pool-%d").build())
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(Integer.MAX_VALUE) // default 25
                        .setMaxConnPerRoute(3000) // default 5
                        .build())
                .build();

        httpClient.start();
    }

    @Override
    @TearDown
    public void stop() throws Exception {
        httpClient.close();
        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(2));
    }

    @Benchmark
    @Override
    public void throughput(OpCounters counters, BasicState basicState, Blackhole blackhole) throws Exception {
        LockSupport.parkNanos(TimeUnit.MICROSECONDS.toNanos(basicState.parkUs));
        doRequest(counters, basicState, blackhole);
    }

    @Override
    public void avgTime(OpCounters counters, BasicState basicState, Blackhole blackhole) throws Exception {

    }

    private void doRequest(OpCounters counters, BasicState basicState, Blackhole blackhole) throws Exception {
        httpClient.execute(SimpleRequestBuilder.get(basicState.url).build(), new FutureCallback<SimpleHttpResponse>() {
            @Override
            public void completed(SimpleHttpResponse response) {
                counters.incrSuccess();
                blackhole.consume(response.getBody().getBodyText());
            }

            @Override
            public void failed(Exception ex) {
                counters.incrFail();
                blackhole.consume(ex);
            }

            @Override
            public void cancelled() {
                counters.incrFail();
            }
        });
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(JMH_Async_Apache.class.getSimpleName())
                // .threads(1)
                .build();

        new Runner(opt).run();
    }

}
