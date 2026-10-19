package com.fortunaball.stat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 통계 맞춤(StatSnapshotService.sync)을 "누가 부르든 한 줄로 세워서" 실행하는 문 역할의 클래스.
 * 서버 시작, 토요일 20:00, 새 회차 저장 직후 - 세 곳이 모두 이 클래스의 refresh() 만 부른다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 서버를 켜면 "회차 수집"과 "통계 맞춤"이 동시에 시작될 수 있다. 두 곳이 같은 통계를 동시에 만들면
 *    같은 대상 회차가 두 번 저장되려다 DB 중복(UNIQUE) 오류가 난다. 그래서 잠금(lock)으로 한 번에 하나만 돌린다.
 *  - 이미 돌고 있으면 건너뛰지 않고 "끝날 때까지 기다린 뒤" 실행한다. 나중에 부른 쪽이 더 새로운 회차를 보고 있을 수 있어서다.
 *  - 오류가 나도 부른 쪽(스케줄러·수집)에는 오류를 넘기지 않는다. 통계 때문에 회차 수집이 실패 처리되면 안 되기 때문이다.
 *    오류는 로그("[통계스냅샷]")에만 남고, sync 는 몇 번을 불러도 결과가 같아서 다음 실행 때 다시 맞춰진다.
 *
 * 📚 배운 내용: ReentrantLock (한 번에 한 스레드만 들어가게 하는 잠금)
 */
@Component
public class StatSnapshotTrigger {

    private static final Logger log = LoggerFactory.getLogger(StatSnapshotTrigger.class);

    private final StatSnapshotService snapshotService;
    private final ReentrantLock lock = new ReentrantLock();

    public StatSnapshotTrigger(StatSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    /**
     * 통계를 지금 상황에 맞춘다.
     * @param reason 누가 불렀는지 (로그용). 예: "서버 시작", "토요일 20:00", "새 회차 저장"
     * @return 오류 없이 끝났으면 true
     */
    public boolean refresh(String reason) {
        lock.lock();
        try {
            log.info("[통계스냅샷] 맞춤 시작 - {}", reason);
            snapshotService.sync();
            log.info("[통계스냅샷] 맞춤 끝 - {}", reason);
            return true;
        } catch (Throwable e) {
            log.error("[통계스냅샷] 맞춤 중 오류 - {}: {}", reason, e.toString(), e);
            return false;
        } finally {
            lock.unlock();
        }
    }
}
