package com.wordmatch.dto;

import com.wordmatch.domain.FinishResult;

import java.util.List;

/**
 * 下发给客户端的房间投影。
 * 红线：不包含本轮任何一方尚未揭晓的词，也不包含 playerId。
 */
public record RoomSnapshot(
        String code,
        String status,
        String phase,
        int roundNo,
        int maxRounds,
        int roundSeconds,
        String anchorMode,
        List<String> anchor,
        long deadlineAt,
        long serverNow,
        List<PlayerView> players,
        List<ChainView> chain,
        List<String> usedWords,
        int mySlot,
        boolean host,
        FinishResult result
) {

    public record PlayerView(int slot, String nickname, boolean online, boolean submitted, boolean me) {
    }

    public record ChainView(int roundNo, List<String> words, boolean isMatch, boolean timedOut) {
    }
}
