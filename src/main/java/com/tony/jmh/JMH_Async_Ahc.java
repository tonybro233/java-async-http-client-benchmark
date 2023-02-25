package com.tony.jmh;

import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Response;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.asynchttpclient.Dsl.asyncHttpClient;
import static org.asynchttpclient.Dsl.config;


@State(Scope.Benchmark)
public class JMH_Async_Ahc extends JMH_Async {

    private AsyncHttpClient httpClient;

    @Param({"2", "4"})
    public int ioThreads;

    @Setup
    @Override
    public void prepare(BasicState basicState) throws Exception {
        httpClient = asyncHttpClient(config()
                // .setSslContext(SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build())
                .setUseInsecureTrustManager(true)
                .setFollowRedirect(true)
                .setKeepAlive(true)
                .setMaxConnections(10000)
                .setThreadPoolName("custom_name")
                .setIoThreadsCount(ioThreads)
                .setConnectTimeout(10_000));
    }

    @TearDown
    public void stop() throws Exception {
        // System.out.print(" remove client");
        httpClient.close();
        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(2));
    }

    @Benchmark
    @Override
    public void throughput(OpCounters counters, BasicState basicState, Blackhole blackhole) throws Exception {
        LockSupport.parkNanos(TimeUnit.MICROSECONDS.toNanos(basicState.parkUs));
        doRequest(counters, basicState, blackhole);
    }

    // @Benchmark
    @Override
    public void avgTime(OpCounters counters, BasicState basicState, Blackhole blackhole) throws Exception {
        LockSupport.parkNanos(TimeUnit.MICROSECONDS.toNanos(basicState.parkUs));
        doRequest(counters, basicState, blackhole);
    }

    private void doRequest(OpCounters counters, BasicState basicState, Blackhole blackhole) throws Exception {
        httpClient.prepareGet(basicState.url)
                .execute(new AsyncCompletionHandler<>() {

                    @Override
                    public void onThrowable(Throwable t) {
                        counters.incrFail();
                        blackhole.consume(t);
                    }

                    @Override
                    public Object onCompleted(Response response) throws Exception {
                        counters.incrSuccess();
                        String resp = response.getResponseBody();
                        blackhole.consume(resp);
                        return resp;
                    }
                });
    }


    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(JMH_Async_Ahc.class.getSimpleName())
                .threads(1)
                .build();

        new Runner(opt).run();
    }

}
