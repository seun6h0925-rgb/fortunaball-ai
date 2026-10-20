package com.fortunaball.ai.usage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 사용 기록 한 건. (한도 계산과 관리자 AI 사용량 화면의 원본)
 * DB 테이블: ai_usage_log
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 사용자 질문·꿈 원문·AI 응답 원문, 사용자 식별값(guest_token·IP)은 저장하지 않는다. (정책·개인정보)
 *  - 모델 이름이 있는 행(SUCCESS·FAILED)만 "AI 호출 1회"로 센다. BLOCKED 는 AI 를 부르지 않았으므로 호출 수·비용에 넣지 않는다.
 *  - estimated_cost 의 단위는 USD(달러)다. (테이블 설명에는 단위가 없어서 여기에 적어 둔다)
 *  - createdAt 은 DB 기본값 대신 코드가 서울 시간으로 넣는다. "오늘 호출 수·이번 달 비용"을 서울 날짜 기준으로 세기 위해서다.
 *    (DB 서버 시간대에 따라 하루가 어긋나는 것을 막는다)
 */
@Entity
@Table(name = "ai_usage_log")
public class AiUsageLog {

    /** 오류 사유 저장 길이 제한. 외부 응답이 길어도 로그 테이블이 불어나지 않게 한다. */
    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_type", nullable = false, updatable = false, length = 30)
    private AiFeature featureType;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false, updatable = false, length = 20)
    private AiRequestStatus requestStatus;

    @Column(name = "model_name", updatable = false, length = 50)
    private String modelName;

    @Column(name = "policy_code", updatable = false, length = 50)
    private String policyCode;

    @Column(name = "input_token_count", nullable = false, updatable = false)
    private int inputTokenCount;

    @Column(name = "output_token_count", nullable = false, updatable = false)
    private int outputTokenCount;

    @Column(name = "response_time_ms", updatable = false)
    private Integer responseTimeMs;

    @Column(name = "estimated_cost", updatable = false, precision = 10, scale = 6)
    private BigDecimal estimatedCost;

    @Column(name = "error_message", updatable = false, columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AiUsageLog() {
        // JPA 가 객체를 만들 때 쓰는 기본 생성자
    }

    /** AI 호출 성공 */
    public static AiUsageLog success(AiFeature feature, String modelName, int inputTokens, int outputTokens,
                                     long responseTimeMs, BigDecimal estimatedCost, LocalDateTime now) {
        AiUsageLog log = new AiUsageLog();
        log.featureType = feature;
        log.requestStatus = AiRequestStatus.SUCCESS;
        log.modelName = modelName;
        log.inputTokenCount = inputTokens;
        log.outputTokenCount = outputTokens;
        log.responseTimeMs = (int) Math.min(responseTimeMs, Integer.MAX_VALUE);
        log.estimatedCost = estimatedCost;
        log.createdAt = now;
        return log;
    }

    /** AI 를 불렀지만 실패. 토큰 수를 알 수 없으므로 0, 비용은 0 으로 본다. */
    public static AiUsageLog failed(AiFeature feature, String modelName, long responseTimeMs,
                                    String errorMessage, LocalDateTime now) {
        AiUsageLog log = new AiUsageLog();
        log.featureType = feature;
        log.requestStatus = AiRequestStatus.FAILED;
        log.modelName = modelName;
        log.responseTimeMs = (int) Math.min(responseTimeMs, Integer.MAX_VALUE);
        log.estimatedCost = BigDecimal.ZERO;
        log.errorMessage = cut(errorMessage);
        log.createdAt = now;
        return log;
    }

    /** AI 를 부르지 않고 막음. 모델 이름이 없으므로 호출 수·비용에 들어가지 않는다. */
    public static AiUsageLog blocked(AiFeature feature, String policyCode, LocalDateTime now) {
        AiUsageLog log = new AiUsageLog();
        log.featureType = feature;
        log.requestStatus = AiRequestStatus.BLOCKED;
        log.policyCode = policyCode;
        log.createdAt = now;
        return log;
    }

    private static String cut(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }

    public Long getId() { return id; }
    public AiFeature getFeatureType() { return featureType; }
    public AiRequestStatus getRequestStatus() { return requestStatus; }
    public String getModelName() { return modelName; }
    public String getPolicyCode() { return policyCode; }
    public int getInputTokenCount() { return inputTokenCount; }
    public int getOutputTokenCount() { return outputTokenCount; }
    public Integer getResponseTimeMs() { return responseTimeMs; }
    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
