package com.wordmatch.service;

import java.util.concurrent.ThreadLocalRandom;

/** 起始锚点词库（第 1 轮的「共同参照物」） */
public final class WordBank {

    private static final String[] WORDS = {
            "夏天", "雨", "学校", "猫", "城市", "夜晚", "音乐", "海", "家", "车站",
            "月亮", "火锅", "游戏", "假期", "童年", "手机", "咖啡", "运动会", "电影", "旅行",
            "作业", "云", "地铁", "超市", "生日", "冬天", "公园", "朋友", "篮球", "厨房"
    };

    private WordBank() {
    }

    public static String random() {
        return WORDS[ThreadLocalRandom.current().nextInt(WORDS.length)];
    }
}
