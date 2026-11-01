package com.wordmatch.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** 每房间单一定时任务（超时结算 / 揭晓后推进），重复调度自动覆盖 */
@Service
public class RoomScheduler {

    private static final Logger log = LoggerFactory.getLogger(RoomScheduler.class);

    private final ScheduledExecutorService pool = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "room-timer");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    public void schedule(String code, long delayMillis, Runnable action) {
        cancel(code);
        ScheduledFuture<?> future = pool.schedule(() -> {
            tasks.remove(code);
            try {
                action.run();
            } catch (Exception e) {
                log.error("房间定时任务执行失败 code={}", code, e);
            }
        }, Math.max(0, delayMillis), TimeUnit.MILLISECONDS);
        tasks.put(code, future);
    }

    public void cancel(String code) {
        ScheduledFuture<?> future = tasks.remove(code);
        if (future != null) {
            future.cancel(false);
        }
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdownNow();
    }
}
