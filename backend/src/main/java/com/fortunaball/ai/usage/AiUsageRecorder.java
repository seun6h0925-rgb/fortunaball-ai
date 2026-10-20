package com.fortunaball.ai.usage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * AI 사용 기록을 남기는 곳. (성공·실패·차단 모두)
 * 📚 배운 내용: 트랜잭션 - DB 변경을 "전부 성공 또는 전부 취소"로 묶는 단위
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 기록은 "새 트랜잭션(REQUIRES_NEW)"으로 따로 저장한다. 호출한 쪽 작업이 나중에 취소돼도 AI 사용 기록은 남아야
 *    한도·비용이 정확하기 때문이다.
 *  - 기록 저장이 실패해도 사용자 응답을 막지 않는다. (오류 로그만 남기고 넘어간다) 다만 이때 한도 집계가 실제보다 적어질 수 있다.
 */
@Component
public class AiUsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(AiUsageRecorder.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final AiUsageLogRepository repository;
    private final TransactionTemplate newTransaction;
    private final Clock clock;

    @Autowired
    public AiUsageRecorder(AiUsageLogRepository repository, PlatformTransactionManager txManager) {
        this(repository, txManager, Clock.system(ZONE));
    }

    /** 테스트용: 시간을 직접 정할 수 있다 */
    AiUsageRecorder(AiUsageLogRepository repository, PlatformTransactionManager txManager, Clock clock) {
        this.repository = repository;
        this.newTransaction = new TransactionTemplate(txManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public void recordSuccess(AiFeature feature, String modelName, int inputTokens, int outputTokens,
                              long responseTimeMs, BigDecimal estimatedCost) {
        save(AiUsageLog.success(feature, modelName, inputTokens, outputTokens, responseTimeMs, estimatedCost, now()));
    }

    public void recordFailed(AiFeature feature, String modelName, long responseTimeMs, String errorMessage) {
        save(AiUsageLog.failed(feature, modelName, responseTimeMs, errorMessage, now()));
    }

    /** AI 를 부르지 않고 막은 경우. policyCode 는 고정 응답 정책 코드 또는 한도 코드다. */
    public void recordBlocked(AiFeature feature, String policyCode) {
        save(AiUsageLog.blocked(feature, policyCode, now()));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private void save(AiUsageLog entry) {
        try {
            newTransaction.executeWithoutResult(status -> repository.save(entry));
        } catch (RuntimeException e) {
            // 기록 실패가 사용자 응답을 막으면 안 된다. 추적용으로 오류 로그만 남긴다.
            log.error("AI 사용 기록 저장 실패: feature={} status={} policy={}",
                    entry.getFeatureType(), entry.getRequestStatus(), entry.getPolicyCode(), e);
        }
    }
}
