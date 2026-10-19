package com.fortunaball.draw.collector;

import com.fortunaball.draw.dto.CollectSummary;
import com.fortunaball.draw.service.DrawCollectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 새 회차를 자동으로 수집하는 스케줄러. (요구사항 BE-01: 추첨 완료 후 최신 회차 자동 수집)
 *
 * 실행 시각 (모두 한국 시간 Asia/Seoul)
 *   - 토요일 20:45          ← 추첨은 20:35쯤, 당첨금 집계는 조금 늦게 나온다
 *   - 토요일 21:00~23:45    15분마다
 *   - 일요일 12:00          놓친 경우를 위한 보강 1회
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 이 스케줄러는 "번호 생성 대상 회차가 바뀌는 시각(토요일 20:00)"과 아무 관계가 없다.
 *    그건 RoundPolicy 가 시계만 보고 계산한다. 여기는 공식 당첨결과를 DB 로 가져오는 시각만 정한다.
 *  - 새 회차를 한 번 저장하고 나면 이후 실행은 이미 있는 회차를 건너뛰고 끝난다. (공식 사이트에 요청을 보내지 않음)
 *  - 서버가 꺼져 있던 시간대에 놓친 회차는, 서버를 켤 때 도는 시작 수집(DrawCollectStartupRunner)이 채운다.
 *  - 시각은 application.yml 의 fortuna.draw.scheduler.* 로 바꿀 수 있다 (테스트 때 짧게 줄이는 용도).
 *    없으면 아래 기본값을 쓴다. cron 형식: 초 분 시 일 월 요일
 *  - fortuna.draw.scheduler.enabled=false 로 스케줄러만 끌 수 있다. (기본은 켜짐)
 *  - 수집이 이미 돌고 있으면 DrawCollectService 가 알아서 건너뛴다. (중복 실행 방지)
 *  - 실행 결과는 scheduler_log(DRAW_COLLECT_SCHEDULED), 통신 실패는 api_error_log 에 남는다.
 *
 * 📚 배운 내용: @Scheduled(cron, zone), @ConditionalOnProperty
 */
@Component
@ConditionalOnProperty(name = "fortuna.draw.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class DrawCollectScheduler {

    private static final Logger log = LoggerFactory.getLogger(DrawCollectScheduler.class);
    private static final String ZONE = "Asia/Seoul";

    private final DrawCollectService collectService;

    public DrawCollectScheduler(DrawCollectService collectService) {
        this.collectService = collectService;
    }

    @Scheduled(cron = "${fortuna.draw.scheduler.cron-sat-first:0 45 20 * * SAT}", zone = ZONE)
    @Scheduled(cron = "${fortuna.draw.scheduler.cron-sat-rest:0 0/15 21-23 * * SAT}", zone = ZONE)
    @Scheduled(cron = "${fortuna.draw.scheduler.cron-sun:0 0 12 * * SUN}", zone = ZONE)
    public void collectNewRounds() {
        log.info("[스케줄러] 새 회차 수집 시작");
        try {
            CollectSummary summary = collectService.collectAll(DrawCollectService.JOB_SCHEDULED);
            log.info("[스케줄러] 새 회차 수집 끝: 새로 저장 {}, 실패 {}건 / {}",
                    summary.saved(), summary.failedRounds().size(), summary.stopReason());
        } catch (RuntimeException e) {
            // 여기서 잡아 주지 않아도 스케줄러는 다음 시각에 다시 돈다. 원인을 로그로 남기기 위해 잡는다.
            log.error("[스케줄러] 새 회차 수집 중 예기치 못한 오류: {}", e.toString(), e);
        }
    }
}
