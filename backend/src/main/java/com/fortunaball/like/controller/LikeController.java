package com.fortunaball.like.controller;

import com.fortunaball.common.guest.GuestToken;
import com.fortunaball.common.response.ApiResponse;
import com.fortunaball.like.dto.LikeStatusResponse;
import com.fortunaball.like.service.LikeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 좋아요(엄지 버튼). GET /api/likes 현황, POST /api/likes 한 번 누르기.
 * 비회원 토큰 헤더(X-Guest-Token)가 필요하다. 한 사람이 하루 최대 5번, 다음 날 새로 시작한다.
 */
@RestController
@RequestMapping("/api/likes")
public class LikeController {

    private final LikeService service;

    public LikeController(LikeService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<LikeStatusResponse> status(GuestToken guest) {
        return ApiResponse.ok(service.status(guest.value()));
    }

    @PostMapping
    public ApiResponse<LikeStatusResponse> press(GuestToken guest, HttpServletRequest http) {
        return ApiResponse.ok(service.press(guest.value(), http.getRemoteAddr()));
    }
}
