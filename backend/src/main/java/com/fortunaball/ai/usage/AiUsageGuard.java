package com.fortunaball.ai.usage;

import com.fortunaball.ai.client.AiProperties;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.security.RequestRateLimiter;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 를 부르기 "직전"에 한도를 검사한다. (비용이 드는 호출을 막는 문지기)
 *
 * 검사 순서 (먼저 걸리는 것에서 멈춘다)
 *  1. 키 없음            → AI-001 (503)
 *  2. 1분 연타           → COMMON-007 (429)
 *  3. 서비스 하루 호출 상한 → AI-002 (503)   ← DB(ai_usage_log)로 센다. 서버를 껐다 켜도 유지
 *  4. 서비스 월 비용 상한  → AI-003 (503)   ← DB 로 센다
 *  5. 사용자별 하루 무료 횟수 → AI-004 (429) ← 서버 메모리로 센다. (guest_token 기준 + 접속지 기준 둘 다)
 * 3·4번에 걸리면 사용자 횟수는 줄지 않는다. (사용자 잘못이 아니므로)
 *
 * 관리자(admin=true)는 1번(키 없음)만 검사하고 2~5번은 모두 건너뛴다. (시연·테스트를 제한 없이 하려는 결정)
 *  - 관리자의 AI 호출도 사용 기록(ai_usage_log)에는 그대로 남는다. 비용 집계가 맞아야 하기 때문이다.
 *  - 서비스 전체 상한(3·4번)도 면제라서, 관리자가 계속 호출하면 월 $10 을 넘길 수 있다. 비용 위험은 알고 선택한 것이다.
 *  - 관리자 여부는 호출하는 쪽이 관리자 로그인(세션)으로 판단해서 넘긴다. (관리자 로그인은 아직 만들기 전이라, 만들 때 연결한다)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 이 검사기는 "AI 를 실제로 부르는 요청"에만 쓴다. 정책 고정 응답(당첨 보장 요구 등)은 AI 를 안 부르므로 횟수를 쓰지 않는다.
 *  - 사용자별 횟수는 메모리라서 서버를 다시 켜면 초기화된다. DB(ai_usage_log)에 사용자 식별값을 저장하지 않기 때문이다. (개인정보 최소화)
 *    그래도 서비스 전체 일일·월 비용 상한은 DB 기준이라 비용 폭주는 막힌다.
 *  - guest_token 은 사용자가 마음대로 바꿀 수 있다. 그래서 접속지(IP) 기준 상한을 따로 두었다. (학교·회사처럼 한 IP 를 여럿이 쓰면
 *    넉넉해야 해서 사용자 한도보다 크게 둔다: fortuna.ai.ip-daily-limit)
 *  - "하루"와 "이번 달"은 서울 시간 기준이다.
 *  - 한도에 걸려 막힌 요청은 BLOCKED 로 기록한다. (관리자 화면의 한도 상태·차단 건수용)
 */
@Component
@EnableConfigurationProperties(AiProperties.class)
public class AiUsageGuard {

    /** BLOCKED 기록에 남기는 한도 코드 (ai_usage_log.policy_code) */
    public static final String CODE_NOT_CONFIGURED = "AI_NOT_CONFIGURED";
    public static final String CODE_DAILY_LIMIT = "AI_DAILY_LIMIT";
    public static final String CODE_MONTHLY_COST_LIMIT = "AI_MONTHLY_COST_LIMIT";
    public static final String CODE_USER_LIMIT = "AI_USER_LIMIT";

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final int MAX_KEYS = 100_000;

    private final AiProperties props;
    private final RequestRateLimiter rateLimiter;
    private final AiUsageLogRepository repository;
    private final AiUsageRecorder recorder;
    private final Clock clock;
    /** 키 = "날짜:식별값". 날짜가 키에 들어 있어 자정이 지나면 새로 센다. 오래된 키는 만료로 지워진다. */
    private final Cache<String, AtomicInteger> guestDaily;
    private final Cache<String, AtomicInteger> ipDaily;

    @Autowired
    public AiUsageGuard(AiProperties props, RequestRateLimiter rateLimiter,
                        AiUsageLogRepository repository, AiUsageRecorder recorder) {
        this(props, rateLimiter, repository, recorder, Clock.system(ZONE));
    }

    /** 테스트용: 시간을 직접 정할 수 있다 */
    AiUsageGuard(AiProperties props, RequestRateLimiter rateLimiter,
                 AiUsageLogRepository repository, AiUsageRecorder recorder, Clock clock) {
        this.props = props;
        this.rateLimiter = rateLimiter;
        this.repository = repository;
        this.recorder = recorder;
        this.clock = clock;
        this.guestDaily = Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(26)).maximumSize(MAX_KEYS).build();
        this.ipDaily = Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(26)).maximumSize(MAX_KEYS).build();
    }

    /**
     * AI 호출 1건(사용자 요청 1건)을 허용할지 검사한다. 허용되면 사용자 횟수를 1 쓴다. 안 되면 BusinessException.
     *
     * @param guestKey 비회원 토큰 값 (로그에 남기지 않는다)
     * @param clientIp 접속지 (로그에 남기지 않는다)
     */
    public void check(AiFeature feature, String guestKey, String clientIp) {
        check(feature, guestKey, clientIp, false);
    }

    /**
     * @param admin 관리자 요청이면 true. 키 없음만 검사하고 나머지 한도·횟수 제한은 모두 건너뛴다.
     */
    public void check(AiFeature feature, String guestKey, String clientIp, boolean admin) {
        // 1. 키 없음
        if (!props.hasApiKey()) {
            block(feature, CODE_NOT_CONFIGURED);
            throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED, ErrorCode.AI_NOT_CONFIGURED.getDefaultMessage(),
                    "AI_API_KEY 가 비어 있음");
        }

        // 관리자는 여기서 통과. (횟수를 세지도 않는다)
        if (admin) {
            return;
        }

        // 2. 1분 연타 (사용자·접속지 따로)
        rateLimiter.check("ai-guest", guestKey, props.perMinuteLimit());
        rateLimiter.check("ai-ip", clientIp, props.perMinuteLimit());

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        // 3. 서비스 전체 하루 호출 상한
        long calls = repository.countCallsSince(today.atStartOfDay());
        if (calls >= props.dailyCallLimit()) {
            block(feature, CODE_DAILY_LIMIT);
            throw new BusinessException(ErrorCode.AI_DAILY_LIMIT, ErrorCode.AI_DAILY_LIMIT.getDefaultMessage(),
                    "일일 호출 상한 도달: calls=" + calls + " limit=" + props.dailyCallLimit());
        }

        // 4. 서비스 전체 월 비용 상한 (이번 달 1일 0시부터)
        BigDecimal cost = repository.sumCostSince(today.withDayOfMonth(1).atStartOfDay());
        if (cost != null && cost.compareTo(props.monthlyCostLimitUsd()) >= 0) {
            block(feature, CODE_MONTHLY_COST_LIMIT);
            throw new BusinessException(ErrorCode.AI_MONTHLY_COST_LIMIT,
                    ErrorCode.AI_MONTHLY_COST_LIMIT.getDefaultMessage(),
                    "월 비용 상한 도달: cost=" + cost + " limit=" + props.monthlyCostLimitUsd());
        }

        // 5. 사용자별 하루 무료 횟수 (guest_token 기준, 접속지 기준)
        int guestCount = increment(guestDaily, today, guestKey);
        int ipCount = increment(ipDaily, today, clientIp);
        if (guestCount > props.userDailyLimit() || ipCount > props.ipDailyLimit()) {
            block(feature, CODE_USER_LIMIT);
            // 식별값 자체는 남기지 않고 횟수만 남긴다.
            throw new BusinessException(ErrorCode.AI_USER_LIMIT, ErrorCode.AI_USER_LIMIT.getDefaultMessage(),
                    "사용자별 일일 횟수 초과: guestCount=" + guestCount + "/" + props.userDailyLimit()
                            + " ipCount=" + ipCount + "/" + props.ipDailyLimit());
        }
    }

    private static int increment(Cache<String, AtomicInteger> cache, LocalDate today, String id) {
        return cache.get(today + ":" + id, k -> new AtomicInteger()).incrementAndGet();
    }

    private void block(AiFeature feature, String code) {
        recorder.recordBlocked(feature, code);
    }
}
