package com.wordmatch.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** playerId -> WebSocket 会话，以及 session -> player 绑定 */
@Component
public class SessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);

    public record Binding(String playerId, String code) {
    }

    private final Map<String, WebSocketSession> sessionByPlayer = new ConcurrentHashMap<>();
    private final Map<String, Binding> bindingBySession = new ConcurrentHashMap<>();

    public void bind(String playerId, String code, WebSocketSession session) {
        WebSocketSession old = sessionByPlayer.put(playerId, session);
        if (old != null && old.isOpen() && !old.getId().equals(session.getId())) {
            // 同一玩家新标签页接管旧连接
            try {
                old.close();
            } catch (Exception ignored) {
                // ignore
            }
            bindingBySession.remove(old.getId());
        }
        bindingBySession.put(session.getId(), new Binding(playerId, code));
    }

    public Binding binding(String sessionId) {
        return bindingBySession.get(sessionId);
    }

    public Binding unbind(WebSocketSession session) {
        Binding binding = bindingBySession.remove(session.getId());
        if (binding != null) {
            // 只在当前绑定的就是这条连接时移除，避免误删「已接管」的新连接
            sessionByPlayer.remove(binding.playerId(), session);
        }
        return binding;
    }

    public boolean isOnline(String playerId) {
        WebSocketSession session = sessionByPlayer.get(playerId);
        return session != null && session.isOpen();
    }

    public void send(String playerId, String json) {
        WebSocketSession session = sessionByPlayer.get(playerId);
        if (session == null) {
            return;
        }
        send(session, json);
    }

    public void send(WebSocketSession session, String json) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.warn("推送失败 session={} err={}", session.getId(), e.getMessage());
        }
    }
}
