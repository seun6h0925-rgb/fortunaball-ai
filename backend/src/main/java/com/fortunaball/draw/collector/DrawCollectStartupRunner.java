package com.fortunaball.draw.collector;

import com.fortunaball.draw.service.DrawCollectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 서버가 다 켜진 직후, 빠진 회차를 공식 사이트에서 채우기 시작하는 클래스. (최초 구축·집 컴퓨터 이전 때 1회~최신 회차를 채운다)
 * 📚 배운 내용: 이벤트 리스너 - 스프링이 "서버 준비 완료(ApplicationReadyEvent)" 신호를 보내면 이 메서드가 실행된다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 수집은 별도 스레드(백그라운드)에서 돈다. 1245회를 요청 간격을 두고 가져오면 몇 분이 걸리는데,
 *    같은 스레드에서 돌리면 그동안 서버 화면(API)이 응답하지 않기 때문이다.
 *  - 데몬 스레드라서 서버를 끄면 수집도 함께 멈춘다. 이미 저장된 회차는 다음에 켤 때 건너뛰고 이어서 채운다.
 *  - 이미 다 채워져 있으면 DB 확인만 하고 공식 사이트에는 최신 회차 다음 한 번만 요청해서 금방 끝난다.
 *  - 끄고 싶으면 설정 fortuna.draw.collect-on-startup=false (기본은 true).
 *  - 예상 못 한 오류가 나도 서버는 계속 살아 있고, 오류는 로그("[회차수집]")에 남는다.
 */
@Component
public class DrawCollectStartupRunner {

    private static final Logger log = LoggerFactory.getLogger(DrawCollectStartupRunner.class);

    private final DrawCollectService collectService;
    private final boolean enabled;

    public DrawCollectStartupRunner(
            DrawCollectService collectService,
            @Value("${fortuna.draw.collect-on-startup:true}") boolean enabled) {
        this.collectService = collectService;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!enabled) {
            log.info("[회차수집] 서버 시작 시 자동 수집이 꺼져 있음 (fortuna.draw.collect-on-startup=false)");
            return;
        }
        Thread worker = new Thread(this::runCollect, "draw-collect-startup");
        worker.setDaemon(true);
        worker.start();
    }

    private void runCollect() {
        try {
            log.info("[회차수집] 서버 시작 후 자동 수집을 시작합니다");
            collectService.collectAll();
        } catch (Throwable e) {
            log.error("[회차수집] 자동 수집 중 예상치 못한 오류", e);
        }
    }
}
