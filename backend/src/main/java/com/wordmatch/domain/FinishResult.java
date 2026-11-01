package com.wordmatch.domain;

/** 终局结果 */
public class FinishResult {

    /** WIN | ROUND_LIMIT | GIVE_UP */
    public String type;
    public int rounds;
    /** S / A / B / C，仅 WIN 时有值 */
    public String grade;
    /** 放弃者昵称，仅 GIVE_UP 时有值 */
    public String byNickname;
    public long finishedAt;

    public FinishResult() {
    }

    public FinishResult(String type, int rounds, String grade, String byNickname) {
        this.type = type;
        this.rounds = rounds;
        this.grade = grade;
        this.byNickname = byNickname;
        this.finishedAt = System.currentTimeMillis();
    }
}
