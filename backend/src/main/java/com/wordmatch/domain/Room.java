package com.wordmatch.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 房间领域对象，只存在于服务端存储中，永远不直接下发给客户端。
 * 客户端看到的是 {@link com.wordmatch.dto.RoomSnapshot} 投影。
 */
public class Room {

    public String code;
    public RoomStatus status = RoomStatus.WAITING;
    public Phase phase;
    /** 当前回合号，未开始时为 0 */
    public int roundNo;
    public int maxRounds = 15;

    public String hostPlayerId;

    /** RANDOM | CUSTOM | NONE */
    public String anchorMode = "RANDOM";
    /** 自定义起始锚点（仅 CUSTOM 生效） */
    public String startAnchor;

    /** 当前锚点：第 1 轮是起始词，之后是上一轮的两个词 */
    public List<String> anchor = new ArrayList<>();

    /** 回合截止时间（服务器毫秒） */
    public long deadlineAt;

    public List<Player> players = new ArrayList<>();
    public List<ChainItem> chain = new ArrayList<>();

    /** 归一化后的历史词，用于禁止复用（含锚点词） */
    public List<String> usedWords = new ArrayList<>();

    /** 本轮提交（playerId -> 原始词），绝不进入快照 */
    public Map<String, String> guesses = new HashMap<>();

    public FinishResult result;

    public long createdAt;
    public long updatedAt;

    public Player player(String playerId) {
        if (playerId == null) {
            return null;
        }
        for (Player p : players) {
            if (playerId.equals(p.id)) {
                return p;
            }
        }
        return null;
    }

    public Player playerBySlot(int slot) {
        for (Player p : players) {
            if (p.slot == slot) {
                return p;
            }
        }
        return null;
    }

    public boolean isFull() {
        return players.size() >= 2;
    }

    public boolean allSubmitted() {
        if (players.size() < 2) {
            return false;
        }
        for (Player p : players) {
            if (!p.submitted) {
                return false;
            }
        }
        return true;
    }
}
