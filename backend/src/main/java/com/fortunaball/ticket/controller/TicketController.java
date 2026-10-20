package com.fortunaball.ticket.controller;

import com.fortunaball.common.response.ApiResponse;
import com.fortunaball.ticket.dto.TicketCheckRequest;
import com.fortunaball.ticket.dto.TicketCheckResponse;
import com.fortunaball.ticket.service.TicketCheckService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로또 용지 API.
 *  - POST /api/tickets/check  용지 QR 글자로 당첨 확인 (API-45, 조회 횟수 제한)
 *
 * 컨트롤러는 입력을 받아 서비스에 넘기고 결과를 감싸서 돌려주는 일만 한다.
 */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketCheckService service;

    public TicketController(TicketCheckService service) {
        this.service = service;
    }

    @PostMapping("/check")
    public ApiResponse<TicketCheckResponse> check(@RequestBody TicketCheckRequest request,
                                                  HttpServletRequest http) {
        return ApiResponse.ok(service.check(request, http.getRemoteAddr()));
    }
}
