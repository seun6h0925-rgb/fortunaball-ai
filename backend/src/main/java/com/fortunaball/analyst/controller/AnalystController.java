package com.fortunaball.analyst.controller;

import com.fortunaball.analyst.dto.AnalystRequest;
import com.fortunaball.analyst.dto.AnalystResponse;
import com.fortunaball.analyst.service.AnalystService;
import com.fortunaball.common.guest.GuestToken;
import com.fortunaball.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [메뉴 2] AI 로또 애널리스트 (지도 화면의 AI 질문도 같은 주소를 쓰고 context=MAP 으로 구분한다)
 *  - POST /api/ai/analyst  질문에 대한 분석 답변, 번호 생성, 분석 근거를 돌려준다 (API-07)
 *
 * 컨트롤러는 입력을 받아 서비스에 넘기고 결과를 감싸서 돌려주는 일만 한다.
 * 관리자 면제: 로그인한 관리자 세션이면 AiGateway 가 알아서 한도를 면제한다. (컨트롤러는 관여하지 않는다)
 */
@RestController
@RequestMapping("/api/ai")
public class AnalystController {

    private final AnalystService service;

    public AnalystController(AnalystService service) {
        this.service = service;
    }

    @PostMapping("/analyst")
    public ApiResponse<AnalystResponse> analyst(@RequestBody AnalystRequest request, GuestToken guest,
                                                HttpServletRequest http) {
        return ApiResponse.ok(service.answer(request, guest, http.getRemoteAddr()));
    }
}
