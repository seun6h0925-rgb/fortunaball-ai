package com.fortunaball.stat.controller;

import com.fortunaball.common.response.ApiResponse;
import com.fortunaball.stat.dto.PatternStatResponse;
import com.fortunaball.stat.service.StatQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통계 조회 API.
 *  - API-44 GET /api/statistics/patterns?period=20|50|100  (안 보내면 100)
 *
 * period 를 숫자(Integer)가 아닌 문자열로 받는 이유: "abc" 같은 값이 와도 스프링의 기본 오류 형식이 아니라
 * 우리 공통 응답 형식(INVALID_INPUT)으로 돌려주기 위해서다. (검사는 StatQueryService.parsePeriod)
 */
@RestController
@RequestMapping("/api/statistics")
public class StatController {

    private final StatQueryService statQueryService;

    public StatController(StatQueryService statQueryService) {
        this.statQueryService = statQueryService;
    }

    @GetMapping("/patterns")
    public ApiResponse<PatternStatResponse> patterns(@RequestParam(name = "period", required = false) String period) {
        return ApiResponse.ok(statQueryService.getPatterns(StatQueryService.parsePeriod(period)));
    }
}
