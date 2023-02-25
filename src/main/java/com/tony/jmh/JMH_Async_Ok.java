package com.tony.jmh;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

@State(Scope.Benchmark)
public class JMH_Async_Ok extends JMH_Async {

    private OkHttpClient httpClient;
    private ExecutorService executor;

    @Param({"100", "200"})
    public int threads;

    public int maxConnection = 1000;

    @Setup
    @Override
    public void prepare(BasicState basicState) throws Exception {
        // 限制执行的线程
        executor = Executors.newFixedThreadPool(
                threads, new ThreadFactoryBuilder().setNameFormat("my-pool-%d").build());
        Dispatcher dispatcher = new Dispatcher(executor);
        dispatcher.setMaxRequests(Integer.MAX_VALUE);
        dispatcher.setMaxRequestsPerHost(Integer.MAX_VALUE);

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
        builder.connectTimeout(10_000, TimeUnit.MILLISECONDS);
        builder.followRedirects(true);
        builder.dispatcher(dispatcher);
        builder.sslSocketFactory(sslSocketFactory, x509TrustManager);
        builder.connectionPool(connectionPool);

        httpClient = builder.build();
    }

    @TearDown
    @Override
    public void stop() throws Exception {
        executor.shutdown();
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
        LockSupport.parkNanos(TimeUnit.MICROSECONDS.toNanos(basicState.parkUs));
        doRequest(counters, basicState, blackhole);
    }

    private void doRequest(OpCounters counters, BasicState basicState, Blackhole blackhole) {
        Request request = new Request.Builder()
                .url(HttpUrl.get(basicState.url))
                .get().build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                // log.info("Request fail", e);
                // future.completeExceptionally(e);
                blackhole.consume(e);
                counters.incrFail();
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                // log.info("current thread name is {}", Thread.currentThread().getName());
                blackhole.consume(response.body().string());
                response.close();
                counters.incrSuccess();
            }
        });
    }


    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(JMH_Async_Ok.class.getSimpleName())
                // .threads(1)
                .build();

        new Runner(opt).run();
    }

}
