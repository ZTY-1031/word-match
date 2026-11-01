package com.wordmatch.dto;

/** WebSocket 统一消息结构：{ type, payload } */
public record WsMessage(String type, Object payload) {
}
