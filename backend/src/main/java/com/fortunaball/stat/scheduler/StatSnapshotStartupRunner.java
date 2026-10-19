package com.fortunaball.stat.scheduler;

import com.fortunaball.stat.service.StatSnapshotTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 서버가 다 켜진 직후 통계를 한 번 맞추는 클래스.
 * 처음 켜는 날, 서버가 꺼져 있던 사이 시각이 지난 날(토요일 20:00 놓침) 모두 이것으로 상태가 바로잡힌다.
 * 📚 배운 내용: 이벤트 리스너 - 스프링이 "서버 준비 완료(ApplicationReadyEvent)" 신호를 보내면 이 메서드가 실행된다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 별도 스레드에서 돌린다. 서버 켜지는 속도에 영향을 주지 않고, 오류가 나도 서버는 계속 켜져 있다.
 *  - 같은 시각에 회차 수집(DrawCollectStartupRunner)도 시작될 수 있다. 수집이 새 회차를 저장하면
 *    그 직후에 통계를 다시 맞추므로 결과는 맞게 된다. 겹쳐 실행되는 것은 StatSnapshotTrigger 의 잠금이 막는다.
 *  - 끄고 싶으면 fortuna.stat.sync-on-startup=false (기본은 true).
 */
@Component
public class StatSnapshotStartupRunner {

    private static final Logger log = LoggerFactory.getLogger(StatSnapshotStartupRunner.class);

    private final StatSnapshotTrigger trigger;
    private final boolean enabled;

    public StatSnapshotStartupRunner(StatSnapshotTrigger trigger,
                                     @Value("${fortuna.stat.sync-on-startup:true}") boolean enabled) {
        this.trigger = trigger;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!enabled) {
            log.info("[통계스냅샷] 서버 시작 시 자동 맞춤이 꺼져 있음 (fortuna.stat.sync-on-startup=false)");
            return;
        }
        Thread worker = new Thread(() -> trigger.refresh("서버 시작"), "stat-snapshot-startup");
        worker.setDaemon(true);
        worker.start();
    }
}
