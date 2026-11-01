package com.wordmatch.dto;

/** 建房 / 加入房间的凭证 */
public record JoinResult(String code, String playerId, String token, int slot, String nickname, int maxRounds) {
}
