package com.wordmatch.domain;

/** 房间状态 */
public enum RoomStatus {
    /** 等待第二人加入 / 等待房主开始 */
    WAITING,
    /** 对局进行中 */
    PLAYING,
    /** 已结算 */
    FINISHED,
    /** 异常中断 */
    ABORTED
}
