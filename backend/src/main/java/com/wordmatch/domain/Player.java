package com.wordmatch.domain;

public class Player {

    public String id;
    /** 1 或 2，词链按 slot 排序展示 */
    public int slot;
    public String nickname;
    public boolean online;
    /** 本回合是否已提交（只暴露布尔，不暴露词） */
    public boolean submitted;
    public long joinedAt;

    public Player() {
    }

    public Player(String id, int slot, String nickname) {
        this.id = id;
        this.slot = slot;
        this.nickname = nickname;
        this.online = true;
        this.joinedAt = System.currentTimeMillis();
    }
}
