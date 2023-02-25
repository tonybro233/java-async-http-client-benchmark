package com.tony.jmh;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

@State(Scope.Benchmark)
public class JMH_Async_Jdk extends JMH_Async {

    private HttpClient httpClient;

    private ExecutorService executor;

    @Param({"200", "500"})
    public int threads;

    @Setup
    @Override
    public void prepare(BasicState basicState) throws Exception {
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
                .connectTimeout(Duration.of(10000, ChronoUnit.MILLIS))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .executor(executor)
                .build();
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
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(basicState.url))
                .timeout(Duration.ofMinutes(2))
                .GET()
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((b, e) -> {
                    if (e == null) {
                        counters.incrSuccess();
                        blackhole.consume(b.body());
                    } else {
                        counters.incrFail();
                        blackhole.consume(e);
                    }
                });

    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(JMH_Async_Jdk.class.getSimpleName())
                // .threads(1)
                .build();

        new Runner(opt).run();
    }
}
