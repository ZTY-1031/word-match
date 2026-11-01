package com.wordmatch.store;

import com.wordmatch.domain.Room;

/**
 * 房间存储。当前只有内存实现：房间是「短生命周期 + 强过期」的数据，
 * 进程重启就丢，小游戏够用；以后要接 Redis 只需再加一个实现。
 */
public interface RoomStore {

    /** 不存在或已过期返回 null */
    Room find(String code);

    /** 写入并续期 */
    void save(Room room);

    void delete(String code);

    /** 房间码唯一性占位：已存在返回 false */
    boolean createIfAbsent(Room room);

    /** 当前存活房间数，仅用于健康检查 */
    int size();
}
