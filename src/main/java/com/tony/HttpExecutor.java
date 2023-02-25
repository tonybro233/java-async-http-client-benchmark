package com.tony;

import java.util.Map;

public interface HttpExecutor {

    void init(Map<String, String> configs) throws Exception;

    void close() throws Exception;

    void asyncGet(String url, Monitor monitor);

    boolean doSyncGet(String url);

    default void syncGet(String url, Monitor monitor) {
        long st = System.currentTimeMillis();
        boolean success = doSyncGet(url);
        monitor.finishOnce(System.currentTimeMillis() - st, success);
    }

}
