package com.fortunaball.generate.controller;

import com.fortunaball.common.guest.GuestToken;
import com.fortunaball.common.response.ApiResponse;
import com.fortunaball.generate.dto.GenerateRequest;
import com.fortunaball.generate.dto.GenerateResponse;
import com.fortunaball.generate.service.GenerateService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 메뉴 1 "통계 번호 생성" API.
 *  - POST /api/numbers/generate   (헤더 X-Guest-Token 필요)
 *
 * 컨트롤러는 입력을 받아 서비스에 넘기고 결과를 감싸서 돌려주는 일만 한다. 판단은 GenerateService 가 한다.
 * 📚 배운 내용: @RestController, @RequestMapping, @PostMapping, @RequestBody
 */
@RestController
@RequestMapping("/api/numbers")
public class GenerateController {

    private final GenerateService generateService;

    public GenerateController(GenerateService generateService) {
        this.generateService = generateService;
    }

    @PostMapping("/generate")
    public ApiResponse<GenerateResponse> generate(GuestToken guest, @RequestBody GenerateRequest request) {
        return ApiResponse.ok(generateService.generate(guest, request));
    }
}
