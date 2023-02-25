package com.tony;

import com.tony.impl.AhcImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Monitor {

    private static final Logger log = LoggerFactory.getLogger(Monitor.class);

    private int requestCnt;

    private AtomicInteger successCnt;

    private AtomicInteger failCnt;

    private AtomicInteger totalCnt;

    private List<Long> latencies;

    private long startMillis;

    private long endMillis;

    private CountDownLatch latch;

    public Monitor(int requestCnt) {
        this.requestCnt = requestCnt;
        assert requestCnt > 0;
        this.startMillis = -1;
    }

    public void start() {
        this.successCnt = new AtomicInteger();
        this.failCnt = new AtomicInteger();
        this.totalCnt = new AtomicInteger();
        this.latencies = new ArrayList<>(requestCnt);

        this.startMillis = System.currentTimeMillis();
        this.latch = new CountDownLatch(requestCnt);
    }

    public void finishOnce(long latency, boolean success) {
        if (startMillis > 0) {
            latencies.add(latency);
            if (success) {
                successCnt.incrementAndGet();
            } else {
                failCnt.incrementAndGet();
            }
            latch.countDown();
        }
    }

    public String waitAndAnalyse(long timeout) {
        if (startMillis < 0) {
            return "not started";
        }

        boolean res;
        try {
            res = latch.await(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return "Wait interrupted";
        }
        if (res) {
            return analyse(System.currentTimeMillis());
        } else {
            return "time out or failed";
        }
    }

    private String analyse(long end) {
        long time = end - startMillis;
        double ops =  requestCnt * 1000.0 / time ;
        ops = ((long) (ops * 1000)) / 1000.0;
        long min = latencies.stream().mapToLong(l -> l).min().orElse(-1);
        long max = latencies.stream().mapToLong(l -> l).max().orElse(-1);
        double avg = latencies.stream().mapToLong(l -> l).average().orElse(-1);
        avg = ((long) (avg * 1000)) / 1000.0;
        String resStr = String.format(
                "time=%sms ops=%s minLatency=%sms maxLatency=%sms avgLatency=%sms totalCnt=%d success=%d fail=%d",
                // ",%s,%s,%s,%s,%s,%d,%d,%d",
                time, ops, min, max, avg, requestCnt, successCnt.get(), failCnt.get());

        return resStr;
    }

}
