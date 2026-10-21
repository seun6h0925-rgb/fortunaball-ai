package com.fortunaball.notice.controller;

import com.fortunaball.common.response.ApiResponse;
import com.fortunaball.notice.dto.NoticeDetailResponse;
import com.fortunaball.notice.dto.NoticePageResponse;
import com.fortunaball.notice.dto.NoticeSummaryResponse;
import com.fortunaball.notice.service.NoticeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 공지사항 조회 (API-18 목록, API-19 상세). 로그인 없이 볼 수 있다. */
@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService service;

    public NoticeController(NoticeService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<NoticePageResponse<NoticeSummaryResponse>> list(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(service.list(page, size));
    }

    @GetMapping("/{noticeId}")
    public ApiResponse<NoticeDetailResponse> detail(@PathVariable("noticeId") Long noticeId) {
        return ApiResponse.ok(service.detail(noticeId));
    }
}
