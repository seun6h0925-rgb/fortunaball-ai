package com.fortunaball.round.controller;

import com.fortunaball.common.response.ApiResponse;
import com.fortunaball.draw.service.DrawQueryService;
import com.fortunaball.round.dto.CurrentRoundResponse;
import com.fortunaball.round.service.RoundPolicy;
import com.fortunaball.stat.domain.SnapshotStatus;
import com.fortunaball.stat.service.StatQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;

/**
 * 대상 회차 조회 API (API-43). 입력값 없음.
 *  - 분석 기준 회차: 이미 저장된 가장 최근 회차(DrawQueryService 의 캐시 조회 재사용)
 *  - 통계 상태: 대상 회차 통계(stat_round_snapshot)의 실제 상태 (사전 분석 / 결과 대기 / 확정).
 *    통계가 아직 없으면 "사전 분석"으로 안전하게 돌려준다. (StatQueryService.statusOf)
 */
@RestController
@RequestMapping("/api/rounds")
public class RoundController {

    private final RoundPolicy roundPolicy;
    private final DrawQueryService drawQueryService;
    private final StatQueryService statQueryService;

    public RoundController(RoundPolicy roundPolicy, DrawQueryService drawQueryService,
                           StatQueryService statQueryService) {
        this.roundPolicy = roundPolicy;
        this.drawQueryService = drawQueryService;
        this.statQueryService = statQueryService;
    }

    @GetMapping("/current")
    public ApiResponse<CurrentRoundResponse> current() {
        ZonedDateTime now = ZonedDateTime.now(RoundPolicy.ZONE);
        int basisRound = drawQueryService.getLatest().roundNo();
        int targetRound = roundPolicy.targetRound(now);
        SnapshotStatus status = statQueryService.statusOf(targetRound);
        CurrentRoundResponse body = new CurrentRoundResponse(
                targetRound,
                roundPolicy.drawDate(now),
                basisRound,
                roundPolicy.nextSwitchAt(now),
                status.name(),
                status.label());
        return ApiResponse.ok(body);
    }
}
