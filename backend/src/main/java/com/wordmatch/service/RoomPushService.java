package com.wordmatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordmatch.domain.Player;
import com.wordmatch.domain.Room;
import com.wordmatch.dto.WsMessage;
import com.wordmatch.ws.SessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 把房间快照推给房间内每个玩家，每人一份自己的投影 */
@Service
public class RoomPushService {

    private static final Logger log = LoggerFactory.getLogger(RoomPushService.class);

    private final SessionRegistry registry;
    private final RoomService roomService;
    private final ObjectMapper mapper;

    public RoomPushService(SessionRegistry registry, RoomService roomService, ObjectMapper mapper) {
        this.registry = registry;
        this.roomService = roomService;
        this.mapper = mapper;
    }

    public void broadcast(Room room) {
        if (room == null) {
            return;
        }
        for (Player player : room.players) {
            if (!registry.isOnline(player.id)) {
                continue;
            }
            try {
                String json = mapper.writeValueAsString(
                        new WsMessage("room.state", roomService.snapshot(room, player.id)));
                registry.send(player.id, json);
            } catch (Exception e) {
                log.warn("房间快照推送失败 code={} player={}", room.code, player.id, e);
            }
        }
    }

    public void broadcastSafe(Room room) {
        try {
            broadcast(room);
        } catch (Exception e) {
            log.warn("房间快照推送异常 code={}", room == null ? null : room.code, e);
        }
    }
}
