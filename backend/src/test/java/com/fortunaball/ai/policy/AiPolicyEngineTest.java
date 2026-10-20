package com.fortunaball.ai.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 시드(docs/db/seed_ai_policy.sql)와 같은 구조의 정책으로 판별 규칙을 확인한다. */
class AiPolicyEngineTest {

    private static final List<AiPolicy> POLICIES = List.of(
            new AiPolicy("DESPERATE_REQUEST", "DESPERATE_REQUEST", "ALL", 10, "절박 안내", List.of("빚이", "대출", "생활비")),
            new AiPolicy("GUARANTEE_REQUEST", "GUARANTEE_REQUEST", "ALL", 20, "보장 불가 안내", List.of("1등되는번호", "당첨번호알려", "무조건당첨")),
            new AiPolicy("PURCHASE_PUSH", "PURCHASE_PUSH", "ALL", 30, "구매 권유 안 함", List.of("사도돼", "살까", "구매할까")),
            new AiPolicy("LOCATION_REQUIRED", "LOCATION_REQUIRED", "ANALYST", 40, "위치를 켜 주세요", List.of("내위치", "내주변", "근처", "가까운")),
            new AiPolicy("OFF_TOPIC_MAP", "OFF_TOPIC", "ANALYST", 50, "판매점 질문만 가능", List.of("판매점", "명당", "당첨", "1등")),
            new AiPolicy("DREAM_LOW_RELEVANCE", "DREAM_LOW_RELEVANCE", "DREAM", 60, "상징 부족", List.of()),
            new AiPolicy("DREAM_OFF_TOPIC", "OFF_TOPIC", "DREAM", 70, "꿈을 적어 주세요", List.of("꿈", "꿨", "꾼")));

    private final AiPolicyEngine engine = new AiPolicyEngine(() -> POLICIES);

    private String hit(PolicyContext ctx) {
        return engine.check(ctx).map(PolicyHit::policyCode).orElse(null);
    }

    @Test
    void 당첨_보장_요구는_공백이_섞여도_걸린다() {
        assertEquals("GUARANTEE_REQUEST", hit(PolicyContext.analyst("이번 주 1등 되는 번호 좀 알려줘", false, false)));
        assertEquals("GUARANTEE_REQUEST", hit(PolicyContext.analyst("무조건  당첨 되는 조합", false, false)));
    }

    @Test
    void 절박한_요청과_구매_유도를_걸러낸다() {
        assertEquals("DESPERATE_REQUEST", hit(PolicyContext.analyst("빚이 너무 많아서 로또가 필요해요", false, false)));
        assertEquals("PURCHASE_PUSH", hit(PolicyContext.analyst("이 번호로 사도 돼?", false, false)));
    }

    @Test
    void 우선순위가_높은_정책이_먼저_적용된다() {
        // 절박(10)과 보장(20)이 함께 걸리면 절박 안내가 먼저 나간다
        assertEquals("DESPERATE_REQUEST", hit(PolicyContext.analyst("빚이 많아요 1등 되는 번호 알려줘", false, false)));
    }

    @Test
    void 정상_질문은_걸리지_않는다() {
        assertTrue(engine.check(PolicyContext.analyst("최근 20회에서 자주 나온 번호가 궁금해요", false, false)).isEmpty());
        assertTrue(engine.check(PolicyContext.analyst("", false, false)).isEmpty());
        assertTrue(engine.check(PolicyContext.analyst(null, false, false)).isEmpty());
    }

    @Test
    void 위치가_없으면_내_위치_질문은_위치_안내를_받는다() {
        assertEquals("LOCATION_REQUIRED", hit(PolicyContext.analyst("내 위치 기준 가까운 명당 알려줘", true, false)));
    }

    @Test
    void 위치가_있으면_내_위치_질문이_통과한다() {
        assertTrue(engine.check(PolicyContext.analyst("내 위치 기준 가까운 명당 알려줘", true, true)).isEmpty());
    }

    @Test
    void 지도_질문이_판매점과_무관하면_주제_이탈이다() {
        assertEquals("OFF_TOPIC_MAP", hit(PolicyContext.analyst("야 오늘 약속 있는데 맛집 좀 알려줘", true, true)));
        assertEquals("OFF_TOPIC_MAP", hit(PolicyContext.analyst("근처 맛집", true, true)));
    }

    @Test
    void 지도_질문이_판매점_이야기면_통과한다() {
        assertTrue(engine.check(PolicyContext.analyst("1등 많이 나온 판매점 알려줘", true, false)).isEmpty());
    }

    @Test
    void 지도_문맥이_아니면_주제_이탈_정책을_적용하지_않는다() {
        assertTrue(engine.check(PolicyContext.analyst("최근 통계 요약해 줘", false, false)).isEmpty());
        assertTrue(engine.check(PolicyContext.analyst("내 주변 이야기", false, false)).isEmpty());   // 위치 정책도 지도 질문에만 적용
    }

    @Test
    void 꿈풀이에서_상징도_꿈_표현도_없으면_주제_이탈이다() {
        assertEquals("DREAM_OFF_TOPIC", hit(PolicyContext.dream("오늘 점심 뭐 먹을지 고민이에요", false)));
    }

    @Test
    void 꿈풀이에서_꿈_표현이나_상징이_있으면_통과한다() {
        assertTrue(engine.check(PolicyContext.dream("이상한 꿈을 꿨어요", false)).isEmpty());
        assertTrue(engine.check(PolicyContext.dream("돼지가 집에 들어와요", true)).isEmpty());
    }

    @Test
    void 기능이_다른_정책은_적용하지_않는다() {
        // DREAM 전용 정책이 애널리스트 질문에는 걸리지 않는다
        Optional<PolicyHit> hit = engine.check(PolicyContext.analyst("오늘 점심 뭐 먹지", false, false));
        assertTrue(hit.isEmpty());
    }

    @Test
    void 키워드가_없는_정책과_코드가_새로_추가된_정책을_다룬다() {
        AiPolicyEngine custom = new AiPolicyEngine(() -> List.of(
                new AiPolicy("NO_KEYWORDS", "X", "ALL", 1, "m1", List.of()),
                new AiPolicy("ADMIN_ADDED", "X", "ALL", 2, "m2", List.of("금지어"))));
        assertEquals("ADMIN_ADDED", custom.check(PolicyContext.analyst("이건 금지어 입니다", false, false)).orElseThrow().policyCode());
        assertTrue(custom.check(PolicyContext.analyst("평범한 질문", false, false)).isEmpty());
    }
}
