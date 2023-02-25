package com.tony;

import com.tony.impl.*;
import io.netty.util.internal.StringUtil;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;

public class ManualTest {

    private static final Logger log = LoggerFactory.getLogger(ManualTest.class);

    public static void main(String[] args) {
        CmdOptions cmdOptions = new CmdOptions(args);

        String type = cmdOptions.type.orElse(null);
        String url = cmdOptions.url.orElse(null);
        if (StringUtil.isNullOrEmpty(type)) {
            log.error("Please input client type: ahc/apache/jdk/jetty/ok");
            return;
        }
        if (StringUtil.isNullOrEmpty(url)) {
            log.error("Please input url");
            return;
        }

        Boolean sync = cmdOptions.sync.orElse(false);
        Integer requestCnt = cmdOptions.cnt.orElse(10000);
        if (requestCnt <= 0) {
            log.error("Request count should be positive");
            return;
        }
        TimeValue interval = cmdOptions.interval.orElse(TimeValue.milliseconds(1));
        long intervalNs = interval.convertTo(TimeUnit.NANOSECONDS);
        Integer threads = cmdOptions.threads.orElse(1);
        if (threads <= 0) {
            threads = 1;
        }
        Map<String, String> configs = cmdOptions.specs.orElse(Collections.emptyList())
                .stream()
                .map(s -> s.split(":"))
                .collect(Collectors.toMap(a -> a[0], a -> a[1]));
        // System.out.printf("type=%s sync=%s\nrequestCnt=%s\nthreads=%s interval=%s\nconfigs=%s\n",
        //         type, sync, requestCnt, threads, interval, configs);
        log.info("Prepare testing\ntype={} sync={}\nrequestCnt={} threads={} interval={}\nconfigs={}",
                type, sync, requestCnt, threads, interval, configs);

        // 初始化客户端
        HttpExecutor httpExecutor;
        switch (type) {
            case "ahc":
                httpExecutor = new AhcImpl();
                break;
            case "apache":
                if (sync) {
                    httpExecutor = new ApacheSyncImpl();
                } else {
                    httpExecutor = new ApacheAsyncImpl();
                }
                break;
            case "jdk":
                httpExecutor = new JdkImpl();
                break;
            case "jetty":
                httpExecutor = new JettyImpl();
                break;
            case "ok":
                httpExecutor = new OkImpl();
                break;
            default:
                log.error("Please input client type: ahc/apache/jdk/jetty/ok");
                return;
        }
        try {
            httpExecutor.init(configs);
        } catch (Exception e) {
            log.error("Init http client failed", e);
            return;
        }

        // 预热
        for (int j = 1; j <= 5; j++) {
            int warmCnt = 2000;
            // System.out.println(j + " Start warm. loop " + warmCnt + " times");
            log.info("Start warm {}. loop {} times", j, warmCnt);
            Monitor m1 = new Monitor(warmCnt);
            m1.start();
            for (int i = 0; i < warmCnt; i++) {
                if (sync) {
                    httpExecutor.syncGet(url, m1);
                } else {
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                    httpExecutor.asyncGet(url, m1);
                }
            }
            // System.out.println(j + " Warm submit over.");
            log.info("Warm {} submit over", j);
            String s = m1.waitAndAnalyse(Long.MAX_VALUE);
            // System.out.println(j + " Warm: " + s);
            log.info("Warm {} result: {}", j, s);
        }

        // 发起异步调用，回调中更新监控对象
        log.info("Start test");
        Monitor monitor = new Monitor(requestCnt);
        monitor.start();
        int avg = Math.max(1, requestCnt / threads);
        for (int i = 0; i < threads; i++) {
            int cnt = 0;
            if ((i + 1) * avg <= requestCnt) {
                cnt = avg;
            } else {
                cnt = requestCnt - i * avg;
            }
            if (cnt <= 0) {
                break;
            }
            final int fcnt = cnt;
            log.info("Thread-" + i + " will execute " + fcnt + " times");
            new Thread(() -> {
                List<Long> callUse = new ArrayList<>(fcnt);
                for (int j = 0; j < fcnt; j++) {
                    try {
                        if (sync) {
                            httpExecutor.syncGet(url, monitor);
                        } else {
                            long st = System.currentTimeMillis();
                            httpExecutor.asyncGet(url, monitor);
                            callUse.add(System.currentTimeMillis() - st);
                            LockSupport.parkNanos(intervalNs);
                        }
                    } catch (Exception e) {
                        System.err.println("Execution error");
                        e.printStackTrace();
                        return;
                    }
                }
                log.info("{} submit over. max call uses: {}", Thread.currentThread().getName(),
                        callUse.stream().sorted(Comparator.reverseOrder()).limit(50).collect(Collectors.toList()));
            }, "thread-" + i).start();
        }

        // 使用监控对象等待执行完成
        log.info("Waiting test finish");
        String res = monitor.waitAndAnalyse(1000_000);

        // 输出运行状况
        log.info("{} Analyse: {}", type, res);

        // 关闭客户端
        try {
            httpExecutor.close();
        } catch (Exception e) {
            log.error("Close http client error", e);
        }

        log.info("Test over");
        System.exit(0);
    }


    public static class CmdOptions {

        private final  Optional<String> type;

        private final  Optional<Boolean> sync;

        private final  Optional<String> url;

        private final  Optional<Integer> cnt;

        private final  Optional<TimeValue> interval;

        private final  Optional<Integer> threads;

        private final  Optional<List<String>> specs;

        public CmdOptions(String[] args) {
            OptionParser parser = new OptionParser();

            OptionSpec<String> optArgs = parser.nonOptions("http client to run: ahc/apache/jdk/jetty/ok")
                    .ofType(String.class)
                    .describedAs("string");

            OptionSpec<Boolean> optSync = parser.accepts("s", "if use sync mode")
                    .withRequiredArg()
                    .ofType(Boolean.class)
                    .describedAs("boolean");

            OptionSpec<String> optUrl = parser.accepts("u", "test url")
                    .withRequiredArg()
                    .ofType(String.class)
                    .describedAs("string");

            OptionSpec<Integer> optCnt = parser.accepts("cnt", "test query count")
                    .withRequiredArg()
                    .ofType(Integer.class)
                    .describedAs("int");

            OptionSpec<TimeValue> optInterval = parser.accepts("i", "test query interval. like 100ms")
                    .withRequiredArg()
                    .defaultsTo("0")
                    .ofType(TimeValue.class)
                    .describedAs("time");

            OptionSpec<Integer> optThreads = parser.accepts("t", "call threads count")
                    .withRequiredArg()
                    .ofType(Integer.class)
                    .describedAs("int");

            OptionSpec<String> optSpecs = parser.accepts("opt", "specific options, key:value")
                    .withRequiredArg()
                    .withValuesSeparatedBy(",")
                    .ofType(String.class)
                    .describedAs("string");

            OptionSet set = parser.parse(args);
            type = toOptional(optArgs, set);
            sync = toOptional(optSync, set);
            url = toOptional(optUrl, set);
            cnt = toOptional(optCnt, set);
            interval = toOptional(optInterval, set);
            threads = toOptional(optThreads, set);
            specs = toCollectionOptional(optSpecs, set);
        }

        private static <T> Optional<T> toOptional(OptionSpec<T> option, OptionSet set) {
            if (set.has(option)) {
                return Optional.eitherOf(option.value(set));
            }
            return Optional.none();
        }

        private static <T> Optional<List<T>> toCollectionOptional(OptionSpec<T> option, OptionSet set) {
            if (set.has(option)) {
                return Optional.eitherOf(option.values(set));
            }
            return Optional.none();
        }
    }

}
