package com.wordmatch.domain;

/** 回合阶段（服务端权威） */
public enum Phase {
    /** 回合开启，等待双方提交 */
    ROUND_OPEN,
    /** 已判定，正在揭晓 */
    ROUND_REVEAL,
    /** 对局结束 */
    FINISHED
}
