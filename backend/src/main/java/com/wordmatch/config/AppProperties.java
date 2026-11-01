package com.wordmatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** 单回合输入时限（秒） */
    private int roundSeconds = 20;

    /** 房间存活时间（秒） */
    private long roomTtlSeconds = 7200;

    /** 揭晓停留时长（毫秒） */
    private long revealMillis = 2400;

    /** 开发期允许的跨域来源 */
    private List<String> corsOrigins = new ArrayList<>(List.of("http://localhost:5173", "http://127.0.0.1:5173"));

    public int getRoundSeconds() {
        return roundSeconds;
    }

    public void setRoundSeconds(int roundSeconds) {
        this.roundSeconds = roundSeconds;
    }

    public long getRoomTtlSeconds() {
        return roomTtlSeconds;
    }

    public void setRoomTtlSeconds(long roomTtlSeconds) {
        this.roomTtlSeconds = roomTtlSeconds;
    }

    public long getRevealMillis() {
        return revealMillis;
    }

    public void setRevealMillis(long revealMillis) {
        this.revealMillis = revealMillis;
    }

    public List<String> getCorsOrigins() {
        return corsOrigins;
    }

    public void setCorsOrigins(List<String> corsOrigins) {
        this.corsOrigins = corsOrigins;
    }
}
