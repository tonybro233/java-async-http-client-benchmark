package com.tony.jmh;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
public abstract class JMH_Async {

    public static final ThreadLocal<AtomicInteger> cnt1 = new ThreadLocal<>();
    public static final ThreadLocal<AtomicInteger> cnt2 = new ThreadLocal<>();

    public abstract void prepare(BasicState basicState) throws Exception;

    public abstract void stop() throws Exception;

    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public abstract void throughput(OpCounters counters, BasicState basicState, Blackhole blackhole) throws Exception;

    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public abstract void avgTime(OpCounters counters, BasicState basicState, Blackhole blackhole) throws Exception;


    @State(Scope.Benchmark)
    public static class BasicState {

        @Param("http://localhost:2023/echo?greet=hello")
        public String url;

        @Param({"800"})
        public long parkUs;

        @Setup(Level.Iteration)
        public void reset() {
            // System.out.println("重置计数器");
            cnt1.get().set(0);
            cnt2.get().set(0);
        }

    }

    @State(Scope.Thread)
    @AuxCounters(AuxCounters.Type.OPERATIONS)
    public static class OpCounters {
        private AtomicInteger c1 = new AtomicInteger();
        private AtomicInteger c2 = new AtomicInteger();

        public OpCounters() {
            cnt1.set(c1);
            cnt2.set(c2);
        }

        public void reset() {
            c1.set(0);
            c2.set(0);
        }

        public void incrSuccess() {
            c1.incrementAndGet();
        }
        public void incrFail() {
            c2.incrementAndGet();
        }

        // This accessor will also produce a metric
        public int success() {
            // System.out.println("获取success值");
            return c1.get();
        }

        public int fail() {
            return c2.get();
        }

    }

}
