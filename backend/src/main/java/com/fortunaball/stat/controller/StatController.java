package com.fortunaball.stat.controller;

import com.fortunaball.common.response.ApiResponse;
import com.fortunaball.generate.service.GenerateService;
import com.fortunaball.stat.domain.StatPeriod;
import com.fortunaball.stat.dto.NumberStatResponse;
import com.fortunaball.stat.dto.PatternStatResponse;
import com.fortunaball.stat.dto.PrizeTrendResponse;
import com.fortunaball.stat.dto.RegionStatResponse;
import com.fortunaball.stat.dto.StatSummaryResponse;
import com.fortunaball.stat.service.PrizeTrendService;
import com.fortunaball.stat.service.RegionStatService;
import com.fortunaball.stat.service.StatAnalysisService;
import com.fortunaball.stat.service.StatQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통계 조회 API.
 *  - API-44 GET /api/statistics/patterns?period=20|50|100  (안 보내면 100)
 *  - API-09 GET /api/statistics/summary?period=10|50|100|ALL&basisRound=  (안 보내면 100회·최신 회차 기준)
 *  - API-10 GET /api/statistics/numbers?period=10|50|100|ALL  또는  fromRound=&toRound=  (회차 범위가 있으면 범위 우선)
 *  - API-11 GET /api/statistics/prizes?fromRound=&toRound=  (안 보내면 최근 20회, 최대 200회)
 *  - API-41 GET /api/statistics/regions?fromRound=&toRound=  (안 보내면 최근 20회, 최대 200회) 시도별 당첨 건수와 앞 구간 대비 1등 증감률
 *
 * period 를 숫자(Integer)가 아닌 문자열로 받는 이유: "abc" 같은 값이 와도 스프링의 기본 오류 형식이 아니라
 * 우리 공통 응답 형식(INVALID_INPUT)으로 돌려주기 위해서다. (검사는 StatQueryService.parsePeriod)
 */
@RestController
@RequestMapping("/api/statistics")
public class StatController {

    private final StatQueryService statQueryService;
    private final StatAnalysisService analysisService;
    private final PrizeTrendService prizeTrendService;
    private final RegionStatService regionStatService;
    private final GenerateService generateService;

    public StatController(StatQueryService statQueryService, StatAnalysisService analysisService,
                          PrizeTrendService prizeTrendService, RegionStatService regionStatService,
                          GenerateService generateService) {
        this.statQueryService = statQueryService;
        this.analysisService = analysisService;
        this.prizeTrendService = prizeTrendService;
        this.regionStatService = regionStatService;
        this.generateService = generateService;
    }

    @GetMapping("/patterns")
    public ApiResponse<PatternStatResponse> patterns(@RequestParam(name = "period", required = false) String period) {
        PatternStatResponse cards = statQueryService.getPatterns(StatQueryService.parsePeriod(period));
        // 카드마다 "구성 예시" 번호를 붙인다. (번호 생성과 같은 규칙으로 서버가 만든 실제 번호, 실패한 패턴은 예시 없이 나간다)
        return ApiResponse.ok(cards.withExamples(generateService.exampleGames(cards.targetRound())));
    }

    @GetMapping("/summary")
    public ApiResponse<StatSummaryResponse> summary(@RequestParam(name = "period", required = false) String period,
                                                    @RequestParam(name = "basisRound", required = false) Integer basisRound) {
        return ApiResponse.ok(analysisService.summary(StatPeriod.parse(period), basisRound));
    }

    @GetMapping("/numbers")
    public ApiResponse<NumberStatResponse> numbers(@RequestParam(name = "period", required = false) String period,
                                                   @RequestParam(name = "fromRound", required = false) Integer fromRound,
                                                   @RequestParam(name = "toRound", required = false) Integer toRound) {
        return ApiResponse.ok(analysisService.numbers(StatPeriod.parse(period), fromRound, toRound));
    }

    @GetMapping("/prizes")
    public ApiResponse<PrizeTrendResponse> prizes(@RequestParam(name = "fromRound", required = false) Integer fromRound,
                                                  @RequestParam(name = "toRound", required = false) Integer toRound) {
        return ApiResponse.ok(prizeTrendService.prizes(fromRound, toRound));
    }

    @GetMapping("/regions")
    public ApiResponse<RegionStatResponse> regions(@RequestParam(name = "fromRound", required = false) Integer fromRound,
                                                   @RequestParam(name = "toRound", required = false) Integer toRound) {
        return ApiResponse.ok(regionStatService.regions(fromRound, toRound));
    }
}
