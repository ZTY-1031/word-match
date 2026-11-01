package com.wordmatch.service;

import com.wordmatch.config.AppProperties;
import com.wordmatch.domain.ChainItem;
import com.wordmatch.domain.FinishResult;
import com.wordmatch.domain.Phase;
import com.wordmatch.domain.Player;
import com.wordmatch.domain.Room;
import com.wordmatch.domain.RoomStatus;
import com.wordmatch.exception.GameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 回合状态机：开轮 → 提交 → 判定 → 揭晓 → 推进/结算。
 * 所有状态变更都在房间锁内完成，客户端只是投影。
 */
@Service
public class RoundService {

    private static final Logger log = LoggerFactory.getLogger(RoundService.class);

    private final RoomService roomService;
    private final JudgeService judge;
    private final RoomLockManager lockManager;
    private final RoomScheduler scheduler;
    private final RoomPushService push;
    private final AppProperties props;

    public RoundService(RoomService roomService,
                        JudgeService judge,
                        RoomLockManager lockManager,
                        RoomScheduler scheduler,
                        RoomPushService push,
                        AppProperties props) {
        this.roomService = roomService;
        this.judge = judge;
        this.lockManager = lockManager;
        this.scheduler = scheduler;
        this.push = push;
        this.props = props;
    }

    public void start(String code, String playerId) {
        ReentrantLock lock = lockManager.lock(code);
        lock.lock();
        try {
            Room room = roomService.require(code);
            if (!playerId.equals(room.hostPlayerId)) {
                throw new GameException("UNAUTHORIZED", "只有房主可以开始游戏");
            }
            if (!room.isFull()) {
                throw new GameException("ROOM_NOT_FULL", "等待另一位玩家加入");
            }
            if (room.status == RoomStatus.PLAYING) {
                return;
            }
            room.status = RoomStatus.PLAYING;
            room.phase = null;
            room.result = null;
            room.roundNo = 0;
            room.chain.clear();
            room.usedWords.clear();
            room.guesses.clear();
            room.deadlineAt = 0;
            room.players.forEach(p -> p.submitted = false);
            String anchor = roomService.resolveStartAnchor(room);
            room.anchor = new ArrayList<>();
            if (anchor != null) {
                room.anchor.add(anchor);
                addUsed(room, anchor);
            }
            openRound(room);
            log.info("开局 code={} maxRounds={} anchor={}", code, room.maxRounds, room.anchor);
        } finally {
            lock.unlock();
        }
    }

    public void submit(String code, String playerId, Integer roundNo, String word) {
        ReentrantLock lock = lockManager.lock(code);
        lock.lock();
        try {
            Room room = roomService.require(code);
            if (room.status != RoomStatus.PLAYING) {
                throw new GameException("ROOM_NOT_PLAYING", "对局还没开始");
            }
            if (room.phase != Phase.ROUND_OPEN) {
                throw new GameException("ROUND_SETTLED", "本回合已经结束了");
            }
            if (roundNo == null || roundNo != room.roundNo) {
                throw new GameException("ROUND_MISMATCH", "回合已切换，请重新输入");
            }
            Player player = room.player(playerId);
            if (player == null) {
                throw new GameException("UNAUTHORIZED", "你不在这个房间里");
            }
            if (player.submitted) {
                // 幂等：重复提交直接返回当前状态
                push.broadcastSafe(room);
                return;
            }
            String norm = judge.validateAndNormalize(word);
            if (room.usedWords.contains(norm)) {
                throw new GameException("WORD_REPEATED", "这个词之前出现过了");
            }
            room.guesses.put(playerId, word.trim());
            player.submitted = true;
            roomService.save(room);
            if (room.allSubmitted()) {
                settle(room);
            } else {
                push.broadcastSafe(room);
            }
        } finally {
            lock.unlock();
        }
    }

    /** 超时结算：定时器到点后以已提交内容判定，未提交即为弃权轮 */
    public void onTimeout(String code, int roundNo) {
        ReentrantLock lock = lockManager.lock(code);
        lock.lock();
        try {
            Room room = roomService.find(code);
            if (room == null || room.status != RoomStatus.PLAYING
                    || room.phase != Phase.ROUND_OPEN || room.roundNo != roundNo) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now < room.deadlineAt - 50) {
                scheduler.schedule(code, room.deadlineAt - now, () -> onTimeout(code, roundNo));
                return;
            }
            settle(room);
        } finally {
            lock.unlock();
        }
    }

    /** 揭晓播完后推进到下一回合 */
    public void advance(String code, int expectedRound) {
        ReentrantLock lock = lockManager.lock(code);
        lock.lock();
        try {
            Room room = roomService.find(code);
            if (room == null || room.status != RoomStatus.PLAYING
                    || room.phase != Phase.ROUND_REVEAL || room.roundNo != expectedRound) {
                return;
            }
            ChainItem last = room.chain.isEmpty() ? null : room.chain.get(room.chain.size() - 1);
            if (last != null) {
                List<String> nextAnchor = new ArrayList<>();
                for (String w : last.words) {
                    if (w != null && !w.isBlank()) {
                        nextAnchor.add(w);
                    }
                }
                if (!nextAnchor.isEmpty()) {
                    room.anchor = nextAnchor;
                }
            }
            openRound(room);
        } finally {
            lock.unlock();
        }
    }

    public void restart(String code, String playerId) {
        ReentrantLock lock = lockManager.lock(code);
        lock.lock();
        try {
            Room room = roomService.require(code);
            if (room.player(playerId) == null) {
                throw new GameException("UNAUTHORIZED", "你不在这个房间里");
            }
            scheduler.cancel(code);
            room.status = RoomStatus.WAITING;
            room.phase = null;
            room.roundNo = 0;
            room.deadlineAt = 0;
            room.result = null;
            room.chain.clear();
            room.usedWords.clear();
            room.anchor.clear();
            room.guesses.clear();
            room.players.forEach(p -> p.submitted = false);
            roomService.save(room);
            push.broadcastSafe(room);
        } finally {
            lock.unlock();
        }
    }

    public void giveUp(String code, String playerId) {
        ReentrantLock lock = lockManager.lock(code);
        lock.lock();
        try {
            Room room = roomService.find(code);
            if (room == null || room.status != RoomStatus.PLAYING) {
                return;
            }
            Player player = room.player(playerId);
            finish(room, "GIVE_UP", player == null ? null : player.nickname);
        } finally {
            lock.unlock();
        }
    }

    // ---------------------------------------------------------------- 内部

    private void openRound(Room room) {
        room.roundNo = room.roundNo + 1;
        room.phase = Phase.ROUND_OPEN;
        room.guesses.clear();
        room.players.forEach(p -> p.submitted = false);
        room.deadlineAt = System.currentTimeMillis() + props.getRoundSeconds() * 1000L;
        roomService.save(room);
        scheduler.schedule(room.code, props.getRoundSeconds() * 1000L, () -> onTimeout(room.code, room.roundNo));
        push.broadcastSafe(room);
        log.info("开轮 code={} round={}", room.code, room.roundNo);
    }

    /** 结算当前回合，调用方必须已持有房间锁 */
    private void settle(Room room) {
        if (room.phase != Phase.ROUND_OPEN) {
            return;
        }
        room.phase = Phase.ROUND_REVEAL;
        scheduler.cancel(room.code);

        Player p1 = room.playerBySlot(1);
        Player p2 = room.playerBySlot(2);
        String w1 = p1 == null ? null : room.guesses.get(p1.id);
        String w2 = p2 == null ? null : room.guesses.get(p2.id);

        String n1 = w1 == null ? null : judge.normalize(w1);
        String n2 = w2 == null ? null : judge.normalize(w2);
        // null 与任何值（含 null）都不判胜，避免双方挂机白嫖胜利
        boolean match = n1 != null && n2 != null && n1.equals(n2);
        boolean timedOut = w1 == null || w2 == null;

        room.chain.add(new ChainItem(room.roundNo, Arrays.asList(w1, w2), match, timedOut));
        addUsed(room, w1);
        addUsed(room, w2);

        log.info("结算 code={} round={} A={} B={} match={}", room.code, room.roundNo, w1, w2, match);

        if (match) {
            finish(room, "WIN", null);
            return;
        }
        if (room.roundNo >= room.maxRounds) {
            finish(room, "ROUND_LIMIT", null);
            return;
        }
        roomService.save(room);
        push.broadcastSafe(room);
        int round = room.roundNo;
        scheduler.schedule(room.code, props.getRevealMillis(), () -> advance(room.code, round));
    }

    private void finish(Room room, String type, String byNickname) {
        room.status = RoomStatus.FINISHED;
        room.phase = Phase.FINISHED;
        room.deadlineAt = 0;
        scheduler.cancel(room.code);
        String grade = "WIN".equals(type) ? grade(room.roundNo, room.maxRounds) : null;
        room.result = new FinishResult(type, room.roundNo, grade, byNickname);
        roomService.save(room);
        push.broadcastSafe(room);
        log.info("终局 code={} type={} rounds={} grade={}", room.code, type, room.roundNo, grade);
    }

    /** 评级阈值按回合上限等比换算 */
    private String grade(int rounds, int maxRounds) {
        double ratio = maxRounds <= 0 ? 1.0 : (double) rounds / maxRounds;
        if (ratio <= 1.0 / 3) {
            return "S";
        }
        if (ratio <= 2.0 / 3) {
            return "A";
        }
        if (ratio <= 0.87) {
            return "B";
        }
        return "C";
    }

    private void addUsed(Room room, String raw) {
        if (raw == null) {
            return;
        }
        String norm = judge.normalize(raw);
        if (!norm.isEmpty() && !room.usedWords.contains(norm)) {
            room.usedWords.add(norm);
        }
    }
}
