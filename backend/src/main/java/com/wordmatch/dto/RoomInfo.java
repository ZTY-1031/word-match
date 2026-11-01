package com.wordmatch.dto;

import java.util.List;

/** 加入房间前的预览信息 */
public record RoomInfo(String code, String status, boolean full, String hostNickname,
                       int maxRounds, String anchorMode, List<String> players) {
}
