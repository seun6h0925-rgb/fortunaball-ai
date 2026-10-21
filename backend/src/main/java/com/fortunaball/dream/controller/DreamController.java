package com.fortunaball.dream.controller;

import com.fortunaball.common.guest.GuestToken;
import com.fortunaball.common.response.ApiResponse;
import com.fortunaball.dream.dto.DreamRequest;
import com.fortunaball.dream.dto.DreamResponse;
import com.fortunaball.dream.service.DreamService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [메뉴 3] AI 로또 꿈풀이
 *  - POST /api/ai/dream  꿈 이야기로 상징 해석과 참고 번호를 돌려준다 (API-08). 사용자가 말로 답하면 reply·step·keep 등을 이어서 보낸다
 *
 * 컨트롤러는 입력을 받아 서비스에 넘기고 결과를 감싸서 돌려주는 일만 한다.
 * 관리자 면제는 관리자 로그인을 만든 뒤에 연결한다. (지금은 모두 일반 사용자로 처리)
 */
@RestController
@RequestMapping("/api/ai")
public class DreamController {

    private final DreamService service;

    public DreamController(DreamService service) {
        this.service = service;
    }

    @PostMapping("/dream")
    public ApiResponse<DreamResponse> dream(@RequestBody DreamRequest request, GuestToken guest,
                                            HttpServletRequest http) {
        return ApiResponse.ok(service.interpret(request, guest, http.getRemoteAddr()));
    }
}
