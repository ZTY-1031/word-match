package com.wordmatch.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordmatch.config.AppProperties;
import com.wordmatch.domain.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内房间存储。
 * 读写都走一遍 JSON 序列化，避免把同一个对象引用递给多个调用方（模仿外部存储的语义），
 * 这样「校验失败但状态已被改脏」这类问题不会发生。
 */
@Component
public class MemoryRoomStore implements RoomStore {

    private static final Logger log = LoggerFactory.getLogger(MemoryRoomStore.class);

    private final Map<String, Entry> rooms = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final long ttlMillis;

    private record Entry(String json, long expireAt) {
    }

    public MemoryRoomStore(ObjectMapper mapper, AppProperties props) {
        this.mapper = mapper;
        this.ttlMillis = props.getRoomTtlSeconds() * 1000L;
        log.info("房间存储：内存（房间 {} 分钟后过期，服务重启即丢失）", props.getRoomTtlSeconds() / 60);
    }

    @Override
    public Room find(String code) {
        Entry entry = code == null ? null : rooms.get(code);
        if (entry == null) {
            return null;
        }
        if (entry.expireAt() < System.currentTimeMillis()) {
            rooms.remove(code);
            return null;
        }
        try {
            return mapper.readValue(entry.json(), Room.class);
        } catch (Exception e) {
            log.error("房间反序列化失败 code={}", code, e);
            return null;
        }
    }

    @Override
    public void save(Room room) {
        try {
            rooms.put(room.code, new Entry(mapper.writeValueAsString(room), System.currentTimeMillis() + ttlMillis));
        } catch (Exception e) {
            throw new IllegalStateException("房间序列化失败", e);
        }
    }

    @Override
    public void delete(String code) {
        rooms.remove(code);
    }

    @Override
    public boolean createIfAbsent(Room room) {
        try {
            String json = mapper.writeValueAsString(room);
            Entry prev = rooms.putIfAbsent(room.code, new Entry(json, System.currentTimeMillis() + ttlMillis));
            return prev == null;
        } catch (Exception e) {
            throw new IllegalStateException("房间序列化失败", e);
        }
    }

    @Override
    public int size() {
        long now = System.currentTimeMillis();
        return (int) rooms.values().stream().filter(e -> e.expireAt() >= now).count();
    }

    /** 过期房间不主动清会一直占内存，10 分钟扫一次 */
    @Scheduled(fixedDelay = 600_000L, initialDelay = 600_000L)
    public void evictExpired() {
        long now = System.currentTimeMillis();
        int before = rooms.size();
        rooms.entrySet().removeIf(entry -> entry.getValue().expireAt() < now);
        int removed = before - rooms.size();
        if (removed > 0) {
            log.info("清理过期房间 {} 个，剩余 {}", removed, rooms.size());
        }
    }
}
