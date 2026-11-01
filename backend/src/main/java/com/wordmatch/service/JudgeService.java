package com.wordmatch.service;

import com.wordmatch.exception.GameException;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;

/**
 * 词条校验 + 归一化判定。
 * 只做「确定性」判定：不做同义、近义、跨语言、错别字纠正。
 */
@Service
public class JudgeService {

    private static final int MAX_HAN = 12;
    private static final int MAX_ASCII = 20;
    private static final int MAX_MIXED = 16;

    /** 校验并返回归一化结果，非法直接抛 WORD_INVALID */
    public String validateAndNormalize(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new GameException("WORD_INVALID", "还没输入词");
        }
        String trimmed = raw.trim();
        for (int i = 0; i < trimmed.length(); ) {
            int cp = trimmed.codePointAt(i);
            i += Character.charCount(cp);
            if (!isHan(cp) && !isAsciiAlnum(cp)) {
                throw new GameException("WORD_INVALID", "只能输入中文、英文字母和数字");
            }
        }
        int len = trimmed.codePointCount(0, trimmed.length());
        int max = maxLength(trimmed);
        if (len > max) {
            throw new GameException("WORD_INVALID", "最多 " + max + " 个字符");
        }
        String norm = normalize(trimmed);
        if (norm.isEmpty()) {
            throw new GameException("WORD_INVALID", "还没输入词");
        }
        return norm;
    }

    /** 归一化：NFKC → 去空白 → 小写 → 去首尾标点 */
    public String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        s = s.replaceAll("\\s+", "");
        s = s.toLowerCase(Locale.ROOT);
        return stripEdgePunctuation(s);
    }

    private String stripEdgePunctuation(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && isPunctuation(s.codePointAt(start))) {
            start += Character.charCount(s.codePointAt(start));
        }
        while (end > start) {
            int cp = s.codePointBefore(end);
            if (!isPunctuation(cp)) {
                break;
            }
            end -= Character.charCount(cp);
        }
        return s.substring(start, end);
    }

    private boolean isPunctuation(int cp) {
        int type = Character.getType(cp);
        return type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION;
    }

    private boolean isHan(int cp) {
        return Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN;
    }

    private boolean isAsciiAlnum(int cp) {
        return (cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z') || (cp >= '0' && cp <= '9');
    }

    private int maxLength(String value) {
        boolean allHan = true;
        boolean allAscii = true;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            i += Character.charCount(cp);
            if (!isHan(cp)) {
                allHan = false;
            }
            if (!isAsciiAlnum(cp)) {
                allAscii = false;
            }
        }
        if (allHan) {
            return MAX_HAN;
        }
        if (allAscii) {
            return MAX_ASCII;
        }
        return MAX_MIXED;
    }
}
