package com.fortunaball.analyst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fortunaball.analyst.domain.AnalystEvidence;
import com.fortunaball.analyst.domain.EvidenceItem;
import com.fortunaball.analyst.domain.QuestionType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalystAnswerGuardTest {

    private final AnalystAnswerGuard guard = new AnalystAnswerGuard();

    private static AnalystEvidence evidence(int itemCount) {
        List<EvidenceItem> items = new ArrayList<>();
        for (int i = 1; i <= itemCount; i++) {
            items.add(new EvidenceItem("항목" + i, "값 " + "가".repeat(40)));
        }
        return new AnalystEvidence(QuestionType.STAT_ANALYSIS, "1245회까지 최근 100회", items, List.of(), true);
    }

    @Test
    void 쓸_만한_AI_문장은_그대로_쓴다() {
        String ai = "최근 100회에서 자주 나온 번호를 정리했어요. 재미로 참고해 주세요.";
        assertEquals(ai, guard.sanitize("  " + ai + " ", evidence(2)));
    }

    @Test
    void 비어_있으면_근거로_만든_문장을_쓴다() {
        String text = guard.sanitize("", evidence(2), true);
        assertTrue(text.startsWith("1245회까지 최근 100회 기준이에요."));
        assertTrue(text.contains("항목1"));
        assertTrue(text.endsWith("당첨을 보장할 수는 없어요."));
        assertTrue(text.contains("AI가 분석한 내용이지만"));
        assertEquals(text, guard.sanitize(null, evidence(2), true));
        // 일반 대화(번호 생성 아님)의 대체 문장에는 마무리 문장을 붙이지 않는다
        assertTrue(!guard.sanitize("", evidence(2)).contains("보장할 수는 없어요"));
    }

    @Test
    void 단정_표현이_있으면_대체한다() {
        for (String bad : List.of("이 번호는 반드시 나와요.", "당첨 확률이 높아요.", "무조건 좋아요.", "당첨됩니다.",
                "이번 주에 사세요.", "구매하세요.")) {
            String text = guard.sanitize(bad, evidence(1));
            assertTrue(text.contains("항목1"), bad);
        }
    }

    @Test
    void 조회_없이_답한_경우_근거가_없어도_문제없는_문장은_그대로_쓴다() {
        String ai = "안녕하세요! 어떤 번호가 궁금하세요?";
        assertEquals(ai, guard.sanitize(ai, null));
    }

    @Test
    void 조회_없이_답했는데_문제가_있으면_다시_물어보라는_안내() {
        assertEquals(AnalystAnswerGuard.NO_EVIDENCE_FALLBACK, guard.sanitize("무조건 나와요", null));
        assertEquals(AnalystAnswerGuard.NO_EVIDENCE_FALLBACK, guard.sanitize("", null));
    }

    @Test
    void 마무리_문장의_보장할_수는_없어요는_걸리지_않는다() {
        String ai = "번호를 만들었어요." + AnalystAnswerGuard.TAIL;
        assertEquals(ai, guard.sanitize(ai, evidence(1), true));   // 번호를 만든 답변
    }

    @Test
    void 번호를_만들지_않았는데_AI가_마무리_문장을_붙이면_지운다() {
        String ai = "19회가 가장 컸어요." + AnalystAnswerGuard.TAIL;
        assertEquals("19회가 가장 컸어요.", guard.sanitize(ai, evidence(1), false));
        assertEquals(ai, guard.sanitize(ai, evidence(1), true));   // 번호를 만든 답변에는 그대로 둔다
    }

    @Test
    void 평범한_표현은_걸리지_않는다() {
        // "확실" 이 아닌 "확인", "구매" 만 단독으로는 걸리지 않는다
        String ai = "확인해 보니 홀수가 많았어요. 구매 이력은 제공하지 않아요. 재미로 참고해 주세요.";
        assertEquals(ai, guard.sanitize(ai, evidence(1)));
    }

    @Test
    void 너무_길면_대체한다() {
        assertTrue(guard.sanitize("가".repeat(501), evidence(1)).contains("항목1"));
        assertEquals("가".repeat(500), guard.sanitize("가".repeat(500), evidence(1)));
    }

    @Test
    void 대체_문장은_500자를_넘지_않고_항목_중간에서_자르지_않는다() {
        String text = guard.fallbackText(evidence(30), true);
        assertTrue(text.length() <= 500, "길이=" + text.length());
        assertTrue(text.endsWith(AnalystAnswerGuard.TAIL));
        // 마지막 항목은 온전한 "…가." 로 끝난다
        String body = text.substring(0, text.length() - AnalystAnswerGuard.TAIL.length());
        assertTrue(body.endsWith("가."));
    }

    @Test
    void 근거가_없어도_대체_문장이_나온다() {
        AnalystEvidence empty = new AnalystEvidence(QuestionType.STAT_ANALYSIS, null, List.of(), List.of(), true);
        assertEquals(AnalystAnswerGuard.TAIL.trim(), guard.fallbackText(empty, true).trim());
    }
}
