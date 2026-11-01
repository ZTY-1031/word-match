package com.wordmatch.domain;

import java.util.List;

/** 已揭晓的一个回合，词链的最小单元 */
public class ChainItem {

    public int roundNo;
    /** 按 slot 排序：[slot1 的词, slot2 的词]，超时未提交为 null */
    public List<String> words;
    public boolean isMatch;
    public boolean timedOut;

    public ChainItem() {
    }

    public ChainItem(int roundNo, List<String> words, boolean isMatch, boolean timedOut) {
        this.roundNo = roundNo;
        this.words = words;
        this.isMatch = isMatch;
        this.timedOut = timedOut;
    }
}
