package com.fortunaball.draw.controller;

import com.fortunaball.common.response.ApiResponse;
import com.fortunaball.draw.dto.DrawResponse;
import com.fortunaball.draw.service.DrawQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회차 당첨번호 조회 API (메뉴 5 "회차별 당첨번호 조회"의 서버 쪽)
 *
 *  - API-01  GET /api/draws/latest   최신 회차 조회
 *  - API-02  GET /api/draws/{round}  회차 번호로 조회
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - /latest 와 /{round} 는 모양이 겹치지만, Spring 은 글자가 정확히 맞는 주소(/latest)를 먼저 고른다.
 *  - 회차 번호에 숫자가 아닌 값(예: /api/draws/abc)이 오면 공통 오류 처리(GlobalExceptionHandler)가 400 으로 응답한다.
 *  - 컨트롤러는 요청을 받아 서비스에 넘기고 공통 응답 형식으로 감싸기만 한다. 조회·검증은 DrawQueryService 담당.
 */
@RestController
@RequestMapping("/api/draws")
public class DrawController {

    private final DrawQueryService drawQueryService;

    public DrawController(DrawQueryService drawQueryService) {
        this.drawQueryService = drawQueryService;
    }

    /** API-01: 최신 회차 */
    @GetMapping("/latest")
    public ApiResponse<DrawResponse> latest() {
        return ApiResponse.ok(drawQueryService.getLatest());
    }

    /** API-02: 회차별 */
    @GetMapping("/{round}")
    public ApiResponse<DrawResponse> byRound(@PathVariable("round") int round) {
        return ApiResponse.ok(drawQueryService.getByRound(round));
    }
}
