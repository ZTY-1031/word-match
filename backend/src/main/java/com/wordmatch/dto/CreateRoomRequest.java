package com.wordmatch.dto;

public record CreateRoomRequest(String nickname, Integer maxRounds, String anchorMode, String startAnchor) {
}
