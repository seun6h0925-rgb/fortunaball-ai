package com.fortunaball.notice.controller;

import com.fortunaball.common.response.ApiResponse;
import com.fortunaball.notice.dto.AdminNoticeResponse;
import com.fortunaball.notice.dto.NoticeCreateRequest;
import com.fortunaball.notice.dto.NoticePageResponse;
import com.fortunaball.notice.dto.NoticeUpdateRequest;
import com.fortunaball.notice.service.AdminNoticeService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공지사항 관리 (관리자). API-60 목록, API-35 등록, API-36 수정, API-37 삭제.
 * 로그인(관리자 세션)과 CSRF 헤더(X-XSRF-TOKEN)가 필요하다. 검사는 SecurityConfig 에서 한다.
 */
@RestController
@RequestMapping("/api/admin/notices")
public class AdminNoticeController {

    private final AdminNoticeService service;

    public AdminNoticeController(AdminNoticeService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<NoticePageResponse<AdminNoticeResponse>> list(
            @RequestParam(value = "visible", required = false) Boolean visible,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(service.list(visible, page, size));
    }

    @PostMapping
    public ApiResponse<AdminNoticeResponse> create(@RequestBody NoticeCreateRequest request) {
        return ApiResponse.ok(service.create(request), "공지사항을 등록했어요.");
    }

    @PatchMapping("/{noticeId}")
    public ApiResponse<AdminNoticeResponse> update(@PathVariable("noticeId") Long noticeId,
                                                   @RequestBody NoticeUpdateRequest request) {
        return ApiResponse.ok(service.update(noticeId, request), "공지사항을 수정했어요.");
    }

    @DeleteMapping("/{noticeId}")
    public ApiResponse<Void> delete(@PathVariable("noticeId") Long noticeId) {
        service.delete(noticeId);
        return ApiResponse.ok(null, "공지사항을 삭제했어요.");
    }
}
