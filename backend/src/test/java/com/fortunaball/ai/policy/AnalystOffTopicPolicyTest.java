package com.fortunaball.ai.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 애널리스트 주제 이탈(OFF_TOPIC_ANALYST) 판별 규칙 */
class AnalystOffTopicPolicyTest {

    private static final List<AiPolicy> POLICIES = List.of(
            new AiPolicy("OFF_TOPIC_MAP", "OFF_TOPIC", "ANALYST", 50, "지도 이탈", List.of("판매점", "명당")),
            new AiPolicy("OFF_TOPIC_ANALYST", "OFF_TOPIC", "ANALYST", 55, "로또 질문만 가능", List.of("로또", "번호", "통계")));

    private final AiPolicyEngine engine = new AiPolicyEngine(() -> POLICIES);

    private String hit(PolicyContext ctx) {
        return engine.check(ctx).map(PolicyHit::policyCode).orElse(null);
    }

    @Test
    void 허용_단어도_로또_입력도_없으면_걸린다() {
        assertEquals("OFF_TOPIC_ANALYST", hit(PolicyContext.analyst("점심 메뉴 추천해줘", false, false, false)));
    }

    @Test
    void 허용_단어가_공백_사이에_있어도_통과한다() {
        assertTrue(engine.check(PolicyContext.analyst("로 또 어때", false, false, false)).isEmpty());
    }

    @Test
    void 로또_입력이_있으면_단어가_없어도_통과한다() {
        assertTrue(engine.check(PolicyContext.analyst("1245회차 어땠어?", false, false, true)).isEmpty());
    }

    @Test
    void 지도_질문에는_적용하지_않는다() {
        // 지도 질문은 OFF_TOPIC_MAP 이 판단한다. (허용 단어 "판매점"이 있어 통과)
        assertTrue(engine.check(PolicyContext.analyst("판매점 알려줘", true, true, false)).isEmpty());
        assertEquals("OFF_TOPIC_MAP", hit(PolicyContext.analyst("날씨 알려줘", true, true, false)));
    }

    @Test
    void 지도_질문도_이어지는_대화면_지도_이탈로_막지_않는다() {
        // 이전 대화가 있으면 hasLottoInput=true 로 들어온다
        assertTrue(engine.check(PolicyContext.analyst("그럼 거기로 할게", true, true, true)).isEmpty());
    }

    @Test
    void 정책_코드로_정책을_찾아_고정_문구를_돌려준다() {
        assertEquals("로또 질문만 가능", engine.policyOf("OFF_TOPIC_ANALYST").orElseThrow().message());
        assertTrue(engine.policyOf("NOPE").isEmpty());
    }

    @Test
    void 꿈풀이에는_적용하지_않는다() {
        assertTrue(engine.check(PolicyContext.dream("점심 메뉴 추천해줘", true)).isEmpty());
    }
}
