package com.wordmatch.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 按房间码分段加锁，保证「提交 → 判定 → 推进」串行执行。
 * 单实例内有效；多实例部署时需要换成分布式锁。
 */
@Service
public class RoomLockManager {

    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ReentrantLock lock(String code) {
        return locks.computeIfAbsent(code, k -> new ReentrantLock());
    }
}
