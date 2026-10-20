package com.fortunaball.ai.policy;

import com.fortunaball.ai.usage.AiFeature;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * AI 를 부르기 전에 질문을 정책으로 걸러 내는 판별기. AI 호출 없이 규칙(키워드)만 쓴다.
 *
 * 판별 방법
 *  1. 질문에서 공백을 모두 없애고 소문자로 바꾼다. ("1등 되는 번호" → "1등되는번호")
 *  2. 이 기능에 해당하는(scope = ALL 또는 기능 이름) 정책을 priority 작은 순서로 검사한다.
 *  3. 처음 걸린 정책을 돌려준다. 호출한 쪽은 그 message 로 고정 응답하고, AI 호출·횟수 차감 없이 recordBlocked 만 남긴다.
 *
 * 정책 코드별 규칙 (시드: docs/db/seed_ai_policy.sql)
 *  OFF_TOPIC_MAP        지도 질문에서 "허용 키워드"가 하나도 없으면 걸린다.
 *  DREAM_OFF_TOPIC      꿈풀이에서 상징도 없고 "꿈 표현 키워드"도 없으면 걸린다.
 *  OFF_TOPIC_ANALYST    일반(지도가 아닌) 애널리스트 질문에서 로또 입력(번호 생성·회차·발급코드 등)도 없고 "허용 키워드"도 없으면 걸린다.
 *  LOCATION_REQUIRED    지도 질문인데 위치가 없고, 키워드(내 위치·근처 등)가 있으면 걸린다.
 *  DREAM_LOW_RELEVANCE  이 엔진이 판단하지 않는다. (꿈풀이가 상징 유무로 직접 처리)
 *  그 밖의 코드          키워드가 하나라도 들어 있으면 걸린다. (DESPERATE_REQUEST, GUARANTEE_REQUEST, PURCHASE_PUSH,
 *                       관리자가 새로 추가한 정책도 이 규칙을 따른다)
 */
@Component
public class AiPolicyEngine {

    static final String OFF_TOPIC_MAP = "OFF_TOPIC_MAP";
    static final String OFF_TOPIC_ANALYST = "OFF_TOPIC_ANALYST";
    static final String DREAM_OFF_TOPIC = "DREAM_OFF_TOPIC";
    static final String LOCATION_REQUIRED = "LOCATION_REQUIRED";
    static final String DREAM_LOW_RELEVANCE = "DREAM_LOW_RELEVANCE";

    private final AiPolicySource source;

    public AiPolicyEngine(AiPolicySource source) {
        this.source = source;
    }

    public Optional<PolicyHit> check(PolicyContext ctx) {
        String text = normalize(ctx.text());
        List<AiPolicy> policies = source.activePolicies().stream()
                .filter(p -> appliesTo(p, ctx.feature()))
                .sorted(Comparator.comparingInt(AiPolicy::priority))
                .toList();
        for (AiPolicy p : policies) {
            if (isHit(p, ctx, text)) {
                return Optional.of(new PolicyHit(p.code(), p.type(), p.message()));
            }
        }
        return Optional.empty();
    }

    /** 정책 코드로 활성 정책 하나를 찾는다. (AI 가 직접 판단한 주제 이탈처럼, 키워드 없이 고정 문구만 필요할 때 쓴다) */
    public Optional<PolicyHit> policyOf(String code) {
        return source.activePolicies().stream()
                .filter(p -> p.code().equals(code))
                .findFirst()
                .map(p -> new PolicyHit(p.code(), p.type(), p.message()));
    }

    private static boolean appliesTo(AiPolicy p, AiFeature feature) {
        return "ALL".equals(p.scope()) || feature.name().equals(p.scope());
    }

    private static boolean isHit(AiPolicy p, PolicyContext ctx, String text) {
        if (p.keywords().isEmpty() || DREAM_LOW_RELEVANCE.equals(p.code())) {
            return false;
        }
        boolean contains = containsAny(text, p.keywords());
        return switch (p.code()) {
            case OFF_TOPIC_MAP -> ctx.mapContext() && !ctx.hasLottoInput() && !contains;
            case OFF_TOPIC_ANALYST -> ctx.feature() == AiFeature.ANALYST && !ctx.mapContext() && !ctx.hasLottoInput() && !contains;
            case DREAM_OFF_TOPIC -> ctx.feature() == AiFeature.DREAM && !ctx.hasSymbols() && !contains;
            case LOCATION_REQUIRED -> ctx.mapContext() && !ctx.hasLocation() && contains;
            default -> contains;
        };
    }

    private static boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            String k = normalize(keyword);
            if (!k.isEmpty() && text.contains(k)) {
                return true;
            }
        }
        return false;
    }

    static String normalize(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
