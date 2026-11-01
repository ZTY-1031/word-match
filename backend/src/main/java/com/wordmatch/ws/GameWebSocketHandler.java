package com.wordmatch.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordmatch.domain.Player;
import com.wordmatch.domain.Room;
import com.wordmatch.dto.WsMessage;
import com.wordmatch.exception.GameException;
import com.wordmatch.service.RoomPushService;
import com.wordmatch.service.RoomService;
import com.wordmatch.service.RoundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * 唯一实时通道。首帧必须是 {type:"auth", payload:{token}}，token 不放进 URL。
 * 所有下行消息都是 {type, payload}。
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final ObjectMapper mapper;
    private final SessionRegistry registry;
    private final RoomService roomService;
    private final RoundService roundService;
    private final RoomPushService push;

    public GameWebSocketHandler(ObjectMapper mapper,
                                SessionRegistry registry,
                                RoomService roomService,
                                RoundService roundService,
                                RoomPushService push) {
        this.mapper = mapper;
        this.registry = registry;
        this.roomService = roomService;
        this.roundService = roundService;
        this.push = push;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        JsonNode root;
        try {
            root = mapper.readTree(message.getPayload());
        } catch (Exception e) {
            sendError(session, "BAD_MESSAGE", "消息格式错误");
            return;
        }
        String type = root.path("type").asText("");
        JsonNode payload = root.path("payload");

        try {
            switch (type) {
                case "auth" -> handleAuth(session, payload.path("token").asText(""));
                case "round.submit" -> {
                    SessionRegistry.Binding b = requireBinding(session);
                    Integer roundNo = payload.hasNonNull("roundNo") ? payload.get("roundNo").asInt() : null;
                    String word = payload.path("word").isMissingNode() ? null : payload.path("word").asText(null);
                    roundService.submit(b.code(), b.playerId(), roundNo, word);
                }
                case "game.start" -> {
                    SessionRegistry.Binding b = requireBinding(session);
                    roundService.start(b.code(), b.playerId());
                }
                case "game.restart" -> {
                    SessionRegistry.Binding b = requireBinding(session);
                    roundService.restart(b.code(), b.playerId());
                }
                case "game.giveup" -> {
                    SessionRegistry.Binding b = requireBinding(session);
                    roundService.giveUp(b.code(), b.playerId());
                }
                case "ping" -> send(session, new WsMessage("pong", Map.of("t", System.currentTimeMillis())));
                default -> log.debug("忽略未知消息类型 {}", type);
            }
        } catch (GameException e) {
            sendError(session, e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("消息处理异常 type={}", type, e);
            sendError(session, "SERVER_ERROR", "服务端开小差了，重试一下");
        }
    }

    private void handleAuth(WebSocketSession session, String token) {
        int idx = token.indexOf(':');
        if (idx <= 0 || idx == token.length() - 1) {
            throw new GameException("UNAUTHORIZED", "身份信息无效，请重新进入房间");
        }
        String code = token.substring(0, idx);
        String playerId = token.substring(idx + 1);
        Room room = roomService.find(code);
        if (room == null) {
            throw new GameException("ROOM_NOT_FOUND", "房间不存在或已过期");
        }
        Player player = room.player(playerId);
        if (player == null) {
            throw new GameException("UNAUTHORIZED", "你不在这个房间里");
        }
        registry.bind(playerId, code, session);
        if (!player.online) {
            player.online = true;
            roomService.save(room);
        }
        send(session, new WsMessage("auth.ok", Map.of(
                "playerId", playerId,
                "slot", player.slot,
                "snapshot", roomService.snapshot(room, playerId)
        )));
        push.broadcastSafe(room);
    }

    private SessionRegistry.Binding requireBinding(WebSocketSession session) {
        SessionRegistry.Binding binding = registry.binding(session.getId());
        if (binding == null) {
            throw new GameException("UNAUTHORIZED", "连接未鉴权");
        }
        return binding;
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SessionRegistry.Binding binding = registry.unbind(session);
        if (binding == null) {
            return;
        }
        try {
            Room room = roomService.find(binding.code());
            if (room == null) {
                return;
            }
            Player player = room.player(binding.playerId());
            if (player != null) {
                player.online = registry.isOnline(player.id);
                roomService.save(room);
                push.broadcastSafe(room);
            }
        } catch (Exception e) {
            log.warn("断开处理失败 session={}", session.getId(), e);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("WS 传输异常 session={} err={}", session.getId(), exception.getMessage());
    }

    private void sendError(WebSocketSession session, String code, String message) {
        send(session, new WsMessage("error", Map.of("code", code, "message", message == null ? code : message)));
    }

    private void send(WebSocketSession session, WsMessage message) {
        try {
            registry.send(session, mapper.writeValueAsString(message));
        } catch (Exception e) {
            log.warn("下发失败 session={}", session.getId(), e);
        }
    }
}
