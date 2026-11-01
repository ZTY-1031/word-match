package com.wordmatch.controller;

import com.wordmatch.config.AppProperties;
import com.wordmatch.domain.Player;
import com.wordmatch.domain.Room;
import com.wordmatch.dto.ApiResponse;
import com.wordmatch.dto.CreateRoomRequest;
import com.wordmatch.dto.JoinResult;
import com.wordmatch.dto.JoinRoomRequest;
import com.wordmatch.dto.RoomInfo;
import com.wordmatch.exception.GameException;
import com.wordmatch.service.RoomService;
import com.wordmatch.store.RoomStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RoomController {

    private final RoomService roomService;
    private final RoomStore roomStore;
    private final AppProperties props;

    public RoomController(RoomService roomService, RoomStore roomStore, AppProperties props) {
        this.roomService = roomService;
        this.roomStore = roomStore;
        this.props = props;
    }

    /** 建房：返回 code / playerId / token */
    @PostMapping("/rooms")
    public ApiResponse create(@RequestBody(required = false) CreateRoomRequest request) {
        CreateRoomRequest req = request == null ? new CreateRoomRequest(null, null, null, null) : request;
        return ApiResponse.ok(roomService.create(req.nickname(), req.maxRounds(), req.anchorMode(), req.startAnchor()));
    }

    /** 加入前先看房间是否存在、是否满员 */
    @GetMapping("/rooms/{code}")
    public ApiResponse get(@PathVariable String code) {
        Room room = roomService.find(normalizeCode(code));
        if (room == null) {
            return new ApiResponse(1, new RoomInfo(code, "NOT_FOUND", false, null, 0, null, List.of()), "房间不存在或已过期");
        }
        String hostNickname = null;
        List<String> nicknames = room.players.stream().map(p -> p.nickname).toList();
        Player host = room.player(room.hostPlayerId);
        if (host != null) {
            hostNickname = host.nickname;
        }
        RoomInfo info = new RoomInfo(room.code, room.status.name(), room.isFull(), hostNickname,
                room.maxRounds, room.anchorMode, nicknames);
        return ApiResponse.ok(info);
    }

    @PostMapping("/rooms/{code}/join")
    public ApiResponse join(@PathVariable String code, @RequestBody(required = false) JoinRoomRequest request) {
        String nickname = request == null ? null : request.nickname();
        JoinResult result = roomService.join(normalizeCode(code), nickname);
        return ApiResponse.ok(result);
    }

    @GetMapping("/health")
    public ApiResponse health() {
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "store", "memory",
                "rooms", roomStore.size(),
                "roundSeconds", props.getRoundSeconds()
        ));
    }

    private String normalizeCode(String code) {
        if (code == null) {
            throw new GameException("ROOM_NOT_FOUND", "房间号不能为空");
        }
        return code.trim().toUpperCase();
    }
}
