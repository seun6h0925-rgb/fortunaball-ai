package com.fortunaball.dream.service;

import com.fortunaball.dream.domain.DreamKnowledge.MatchedSymbol;
import com.fortunaball.dream.domain.DreamKnowledge.SymbolEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 꿈 내용에서 상징을 찾는다. AI 를 쓰지 않고 상징 이름·별칭 문자열 일치로만 찾는다. (명세서: 별칭·상징 매칭)
 *
 * 규칙
 *  1) 2글자 이상 단어는 글 안에 들어 있으면 일치로 본다.
 *  2) 1글자 단어(용, 뱀, 말, 개, 불, 물, 돈 …)는 "말하다", "새로운", "소리"처럼 다른 낱말의 일부로 자주 나오므로
 *     바로 앞이 한글이 아니고, 바로 뒤가 끝이거나 한글이 아니거나 조사(이·가·을·를·은·는·의·에·도·와·과·랑·만·꿈)일 때만 일치로 본다.
 *     (조사 '로'는 "새로운"을 잡아내므로 뺐다.)
 *  3) 같은 상징은 한 번만 세고, 꿈에 먼저 나온 순서로 최대 MAX_SYMBOLS 개까지 쓴다.
 *
 * ⚠ 한계: "말이 안 돼", "아기자기" 같은 문장은 오인식할 수 있다. 오인식은 관리자 화면에서 별칭을 다듬어 줄인다.
 */
@Component
public class DreamSymbolMatcher {

    /** 한 번에 해석할 최대 상징 수. 상징마다 번호가 4개 안팎이라 3개면 번호 6개가 충분히 채워진다. */
    static final int MAX_SYMBOLS = 3;

    private static final Set<Character> PARTICLES = Set.of('이', '가', '을', '를', '은', '는', '의', '에', '도', '와', '과', '랑', '만', '꿈');

    public List<MatchedSymbol> match(String text, List<SymbolEntry> entries) {
        if (text == null || text.isBlank() || entries == null) {
            return List.of();
        }
        record Hit(long id, String keyword, int index) {
        }
        List<Hit> hits = new ArrayList<>();
        for (SymbolEntry entry : entries) {
            int best = -1;
            List<String> terms = new ArrayList<>();
            terms.add(entry.keyword());
            terms.addAll(entry.aliases());
            for (String term : terms) {
                int idx = firstValidIndex(text, term);
                if (idx >= 0 && (best < 0 || idx < best)) {
                    best = idx;
                }
            }
            if (best >= 0) {
                hits.add(new Hit(entry.id(), entry.keyword(), best));
            }
        }
        hits.sort(Comparator.comparingInt(Hit::index));
        List<MatchedSymbol> result = new ArrayList<>();
        for (Hit hit : hits) {
            if (result.size() >= MAX_SYMBOLS) {
                break;
            }
            result.add(new MatchedSymbol(hit.id(), hit.keyword()));
        }
        return result;
    }

    /** term 이 text 안에서 규칙에 맞게 처음 나오는 위치. 없으면 -1. */
    static int firstValidIndex(String text, String term) {
        if (term == null || term.isBlank()) {
            return -1;
        }
        String t = term.trim();
        int from = 0;
        while (true) {
            int idx = text.indexOf(t, from);
            if (idx < 0) {
                return -1;
            }
            if (t.length() >= 2 || isStandaloneWord(text, idx)) {
                return idx;
            }
            from = idx + 1;
        }
    }

    /** 1글자 단어가 낱말 일부가 아니라 독립된 말(조사가 붙은 형태 포함)인지 본다. */
    private static boolean isStandaloneWord(String text, int idx) {
        if (idx > 0 && isHangul(text.charAt(idx - 1))) {
            return false;
        }
        int next = idx + 1;
        if (next >= text.length()) {
            return true;
        }
        char c = text.charAt(next);
        return !isHangul(c) || PARTICLES.contains(c);
    }

    private static boolean isHangul(char c) {
        return c >= '가' && c <= '힣';
    }
}
