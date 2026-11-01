package com.wordmatch.service;

import com.wordmatch.config.AppProperties;
import com.wordmatch.domain.Player;
import com.wordmatch.domain.Room;
import com.wordmatch.domain.RoomStatus;
import com.wordmatch.dto.JoinResult;
import com.wordmatch.dto.RoomSnapshot;
import com.wordmatch.exception.GameException;
import com.wordmatch.store.RoomStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;

    private final RoomStore store;
    private final AppProperties props;
    private final JudgeService judge;

    public RoomService(RoomStore store, AppProperties props, JudgeService judge) {
        this.store = store;
        this.props = props;
        this.judge = judge;
    }

    public JoinResult create(String nickname, Integer maxRounds, String anchorMode, String startAnchor) {
        String name = sanitizeNickname(nickname, "房主");
        Room room = new Room();
        room.code = allocateCode();
        room.hostPlayerId = null;
        room.maxRounds = clampRounds(maxRounds);
        room.anchorMode = normalizeAnchorMode(anchorMode);
        if ("CUSTOM".equals(room.anchorMode)) {
            String anchor = startAnchor == null ? "" : startAnchor.trim();
            if (anchor.isEmpty()) {
                room.anchorMode = "RANDOM";
            } else {
                // 起始锚点只做长度与字符校验，不参与历史词判定
                room.startAnchor = anchor.length() > 12 ? anchor.substring(0, 12) : anchor;
            }
        }
        room.createdAt = System.currentTimeMillis();
        room.updatedAt = room.createdAt;

        Player host = new Player(UUID.randomUUID().toString(), 1, name);
        room.players.add(host);
        room.hostPlayerId = host.id;

        if (!store.createIfAbsent(room)) {
            throw new GameException("ROOM_CONFLICT", "房间号冲突，请重试");
        }
        store.save(room);
        log.info("创建房间 {} host={}", room.code, name);
        return new JoinResult(room.code, host.id, tokenOf(room.code, host.id), host.slot, host.nickname, room.maxRounds);
    }

    public JoinResult join(String code, String nickname) {
        Room room = require(code);
        if (room.status != RoomStatus.WAITING) {
            throw new GameException("ROOM_CLOSED", "这局已经开始了");
        }
        if (room.isFull()) {
            throw new GameException("ROOM_FULL", "房间已满（2/2）");
        }
        String name = sanitizeNickname(nickname, "玩家2");
        Player player = new Player(UUID.randomUUID().toString(), 2, name);
        room.players.add(player);
        room.updatedAt = System.currentTimeMillis();
        store.save(room);
        log.info("加入房间 {} player={}", room.code, name);
        return new JoinResult(room.code, player.id, tokenOf(room.code, player.id), player.slot, player.nickname, room.maxRounds);
    }

    public Room find(String code) {
        return store.find(code);
    }

    public Room require(String code) {
        Room room = find(code);
        if (room == null) {
            throw new GameException("ROOM_NOT_FOUND", "房间不存在或已过期");
        }
        return room;
    }

    public void save(Room room) {
        room.updatedAt = System.currentTimeMillis();
        store.save(room);
    }

    public void delete(String code) {
        store.delete(code);
    }

    public String tokenOf(String code, String playerId) {
        return code + ":" + playerId;
    }

    public RoomSnapshot snapshot(Room room, String playerId) {
        Player me = room.player(playerId);
        List<RoomSnapshot.PlayerView> players = room.players.stream()
                .sorted(Comparator.comparingInt(p -> p.slot))
                .map(p -> new RoomSnapshot.PlayerView(p.slot, p.nickname, p.online, p.submitted, me != null && p.id.equals(me.id)))
                .toList();
        List<RoomSnapshot.ChainView> chain = room.chain.stream()
                .map(c -> new RoomSnapshot.ChainView(c.roundNo, c.words, c.isMatch, c.timedOut))
                .toList();
        return new RoomSnapshot(
                room.code,
                room.status.name(),
                room.phase == null ? null : room.phase.name(),
                room.roundNo,
                room.maxRounds,
                props.getRoundSeconds(),
                room.anchorMode,
                List.copyOf(room.anchor),
                room.deadlineAt,
                System.currentTimeMillis(),
                players,
                chain,
                List.copyOf(room.usedWords),
                me == null ? 0 : me.slot,
                me != null && me.id.equals(room.hostPlayerId),
                room.result
        );
    }

    /** 起始锚点：NONE 无锚点；CUSTOM 用房主填的词；RANDOM 随机 */
    public String resolveStartAnchor(Room room) {
        if ("NONE".equals(room.anchorMode)) {
            return null;
        }
        if ("CUSTOM".equals(room.anchorMode) && room.startAnchor != null && !room.startAnchor.isBlank()) {
            return room.startAnchor.trim();
        }
        return WordBank.random();
    }

    private String allocateCode() {
        for (int attempt = 0; attempt < 8; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            String code = sb.toString();
            if (store.find(code) == null) {
                return code;
            }
        }
        throw new GameException("ROOM_CONFLICT", "房间号分配失败，请重试");
    }

    private int clampRounds(Integer maxRounds) {
        if (maxRounds == null) {
            return 15;
        }
        return Math.max(1, Math.min(50, maxRounds));
    }

    private String normalizeAnchorMode(String mode) {
        if (mode == null) {
            return "RANDOM";
        }
        String upper = mode.trim().toUpperCase();
        return switch (upper) {
            case "CUSTOM", "NONE" -> upper;
            default -> "RANDOM";
        };
    }

    private String sanitizeNickname(String nickname, String fallback) {
        if (nickname == null) {
            return fallback;
        }
        String name = nickname.replaceAll("[\\p{Cntrl}\\s]", "").trim();
        if (name.isEmpty()) {
            return fallback;
        }
        int count = name.codePointCount(0, name.length());
        if (count > 8) {
            name = name.substring(0, name.offsetByCodePoints(0, 8));
        }
        return name;
    }

    /** 供 RoomController 使用，避免暴露 domain 对象 */
    public int getRoundSeconds() {
        return props.getRoundSeconds();
    }
}
