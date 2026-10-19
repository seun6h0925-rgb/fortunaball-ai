package com.fortunaball.stat.scheduler;

import com.fortunaball.stat.service.StatSnapshotTrigger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 토요일 20:00(서울 시간)에 통계를 맞추는 스케줄러.
 * 이 시각에 "대상 회차"가 다음 회차로 바뀌므로(RoundPolicy), 그에 맞춰
 *   - 방금 닫힌 회차 통계 → 결과 대기(WAITING)
 *   - 새 대상 회차 통계 → 사전 분석(PREPARING)
 * 으로 바꾼다. (실제 변경 내용은 StatSnapshotService.sync 가 정한다)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 이 스케줄러는 공식 당첨결과 수집과 별개다. 수집이 늦어져도 20:00 에는 통계 상태가 바뀐다.
 *  - 당첨결과가 DB 에 저장되는 순간의 맞춤(결과 대기 → 확정)은 여기가 아니라 회차 수집 쪽에서 부른다.
 *  - 시각은 application.yml 의 fortuna.stat.scheduler.cron 으로 바꿀 수 있다 (테스트 때 짧게 줄이는 용도).
 *    cron 형식: 초 분 시 일 월 요일
 *  - fortuna.stat.scheduler.enabled=false 로 이 스케줄러만 끌 수 있다. (기본은 켜짐)
 *  - 서버가 꺼져 있어서 20:00 을 놓쳐도, 서버를 켤 때 한 번 맞추므로 상태가 바로잡힌다.
 *
 * 📚 배운 내용: @Scheduled(cron, zone), @ConditionalOnProperty
 */
@Component
@ConditionalOnProperty(name = "fortuna.stat.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class StatSnapshotScheduler {

    private static final String ZONE = "Asia/Seoul";

    private final StatSnapshotTrigger trigger;

    public StatSnapshotScheduler(StatSnapshotTrigger trigger) {
        this.trigger = trigger;
    }

    @Scheduled(cron = "${fortuna.stat.scheduler.cron:0 0 20 * * SAT}", zone = ZONE)
    public void onSaturdayEvening() {
        trigger.refresh("토요일 20:00");
    }
}
