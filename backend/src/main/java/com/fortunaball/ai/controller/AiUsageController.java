package com.fortunaball.ai.controller;

import com.fortunaball.ai.AiGateway;
import com.fortunaball.ai.dto.AiUsageResponse;
import com.fortunaball.common.guest.GuestToken;
import com.fortunaball.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 이용 현황 API.
 *  - GET /api/ai/usage  오늘 남은 무료 횟수 조회 (헤더 X-Guest-Token 필요)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 조회만 하고 횟수를 쓰지 않는다. 화면이 열릴 때마다 불러도 된다.
 *  - 관리자 여부는 지금 항상 false 다. 관리자 로그인이 만들어지면 세션으로 판단해서 넘긴다.
 *    (사용자가 보내는 값으로 관리자를 판단하면 안 된다)
 */
@RestController
@RequestMapping("/api/ai")
public class AiUsageController {

    private final AiGateway gateway;

    public AiUsageController(AiGateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping("/usage")
    public ApiResponse<AiUsageResponse> usage(GuestToken guest, HttpServletRequest http) {
        return ApiResponse.ok(AiUsageResponse.from(gateway.usage(guest.value(), http.getRemoteAddr(), false)));
    }
}
