package com.fortunaball.payout.controller;

import com.fortunaball.common.response.ApiResponse;
import com.fortunaball.payout.dto.PayoutCalculateRequest;
import com.fortunaball.payout.dto.PayoutCalculateResponse;
import com.fortunaball.payout.service.PayoutService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 당첨금 세금 API.
 *  - POST /api/payout/calculate  회차·등수의 당첨금에 세금을 적용한 참고 실수령액 (API-15)
 *
 * 컨트롤러는 입력을 받아 서비스에 넘기고 결과를 감싸서 돌려주는 일만 한다.
 */
@RestController
@RequestMapping("/api/payout")
public class PayoutController {

    private final PayoutService service;

    public PayoutController(PayoutService service) {
        this.service = service;
    }

    @PostMapping("/calculate")
    public ApiResponse<PayoutCalculateResponse> calculate(@RequestBody PayoutCalculateRequest request) {
        return ApiResponse.ok(service.calculate(request));
    }
}
