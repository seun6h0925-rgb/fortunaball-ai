package com.fortunaball.analyst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fortunaball.analyst.service.AnalystSmallTalk.Kind;
import com.fortunaball.analyst.service.AnalystSmallTalk.Scene;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 인사·감사·사용법 알아보기와 문구 고르기 확인 (AI 없이 계산만). */
class AnalystSmallTalkTest {

    private final AnalystSmallTalk talk = new AnalystSmallTalk();

    @Test
    void 단순한_인사는_알아본다() {
        assertEquals(Optional.of(Kind.GREETING), talk.detect("안녕", false));
        assertEquals(Optional.of(Kind.GREETING), talk.detect("안녕하세요!!", false));
        assertEquals(Optional.of(Kind.GREETING), talk.detect("  Hi ~ ", false));
    }

    @Test
    void 단순한_감사는_알아본다() {
        assertEquals(Optional.of(Kind.THANKS), talk.detect("고마워", false));
        assertEquals(Optional.of(Kind.THANKS), talk.detect("감사합니다!", false));
    }

    @Test
    void 사용법_질문은_단어가_들어_있으면_알아본다() {
        assertEquals(Optional.of(Kind.USAGE), talk.detect("사용법 알려줘", false));
        assertEquals(Optional.of(Kind.USAGE), talk.detect("너 뭘 할 수 있어?", false));
    }

    @Test
    void 인사가_섞인_로또_질문은_알아보지_않는다() {
        assertEquals(Optional.empty(), talk.detect("안녕 1000회 번호 알려줘", true));
        assertEquals(Optional.empty(), talk.detect("안녕 이번 주 번호 만들어줘", false));
        assertEquals(Optional.empty(), talk.detect("1000회 사용법", true));   // 회차가 있으면 사용법 질문이 아니다
    }

    @Test
    void 빈_글이나_애매한_말은_AI에게_맡긴다() {
        assertEquals(Optional.empty(), talk.detect("   ", false));
        assertEquals(Optional.empty(), talk.detect(null, false));
        assertEquals(Optional.empty(), talk.detect("오늘 점심 뭐 먹지", false));
    }

    @Test
    void 첫_인사와_대화_중_인사는_다른_문구_묶음() {
        String first = talk.reply(Kind.GREETING, Scene.GENERAL, true).message();
        String mid = new AnalystSmallTalk().reply(Kind.GREETING, Scene.GENERAL, false).message();

        assertTrue(first.startsWith("안녕하세요"));
        assertFalse(mid.startsWith("안녕하세요"));
    }

    @Test
    void 같은_상황의_문구는_3개를_번갈아_쓴다() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            seen.add(talk.reply(Kind.GREETING, Scene.GENERAL, true).message());
        }
        assertEquals(3, seen.size());
    }

    @Test
    void 사용법_답에만_버튼이_붙는다() {
        assertEquals("OPEN_GUIDE", talk.reply(Kind.USAGE, Scene.GENERAL, true).action());
        assertNull(talk.reply(Kind.GREETING, Scene.GENERAL, true).action());
        assertNull(talk.reply(Kind.THANKS, Scene.GENERAL, false).action());
    }

    @Test
    void 지도_화면은_판매점_문구와_추천_질문() {
        AnalystSmallTalk.Reply r = talk.reply(Kind.USAGE, Scene.MAP, true);
        assertTrue(r.message().contains("내 주변"));
        assertEquals(3, r.suggestions().size());
        assertTrue(r.suggestions().get(0).contains("판매점"));
    }

    @Test
    void 일반_화면_추천_질문은_3개() {
        assertEquals(3, talk.suggestions(Scene.GENERAL).size());
    }

    @Test
    void 주제_이탈_문구도_3개를_번갈아_쓰고_추천_질문이_붙는다() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            AnalystSmallTalk.Reply r = talk.offTopic(Scene.GENERAL);
            assertEquals(3, r.suggestions().size());
            seen.add(r.message());
        }
        assertEquals(3, seen.size());
    }

    @Test
    void 정책_코드는_종류별로_다르다() {
        assertEquals("SMALL_TALK_GREETING", AnalystSmallTalk.codeOf(Kind.GREETING));
        assertEquals("SMALL_TALK_THANKS", AnalystSmallTalk.codeOf(Kind.THANKS));
        assertEquals("USAGE_GUIDE", AnalystSmallTalk.codeOf(Kind.USAGE));
    }
}
