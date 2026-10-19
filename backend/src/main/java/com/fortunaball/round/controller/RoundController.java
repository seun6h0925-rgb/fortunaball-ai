package com.fortunaball.round.controller;

import com.fortunaball.common.response.ApiResponse;
import com.fortunaball.draw.service.DrawQueryService;
import com.fortunaball.round.dto.CurrentRoundResponse;
import com.fortunaball.round.service.RoundPolicy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;

/**
 * 대상 회차 조회 API (API-43). 입력값 없음.
 *  - 분석 기준 회차: 이미 저장된 가장 최근 회차(DrawQueryService 의 캐시 조회 재사용)
 *  - 통계 상태: 통계 저장 기능(stat_round_snapshot)을 만들기 전까지는 "준비 중"으로 고정
 */
@RestController
@RequestMapping("/api/rounds")
public class RoundController {

    private final RoundPolicy roundPolicy;
    private final DrawQueryService drawQueryService;

    public RoundController(RoundPolicy roundPolicy, DrawQueryService drawQueryService) {
        this.roundPolicy = roundPolicy;
        this.drawQueryService = drawQueryService;
    }

    @GetMapping("/current")
    public ApiResponse<CurrentRoundResponse> current() {
        ZonedDateTime now = ZonedDateTime.now(RoundPolicy.ZONE);
        int basisRound = drawQueryService.getLatest().roundNo();
        CurrentRoundResponse body = new CurrentRoundResponse(
                roundPolicy.targetRound(now),
                roundPolicy.drawDate(now),
                basisRound,
                roundPolicy.nextSwitchAt(now),
                "PREPARING",
                "준비 중");
        return ApiResponse.ok(body);
    }
}
