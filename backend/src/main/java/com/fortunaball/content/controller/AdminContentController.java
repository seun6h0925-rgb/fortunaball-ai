package com.fortunaball.content.controller;

import com.fortunaball.common.response.ApiResponse;
import com.fortunaball.content.dto.AdminContentResponse;
import com.fortunaball.content.dto.ContentUpdateRequest;
import com.fortunaball.content.service.AdminContentService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 안내 콘텐츠 관리 (관리자). API-58 목록, API-59 수정.
 * 로그인(관리자 세션)과 CSRF 헤더(X-XSRF-TOKEN)가 필요하다. 검사는 SecurityConfig 에서 한다.
 */
@RestController
@RequestMapping("/api/admin/contents")
public class AdminContentController {

    private final AdminContentService service;

    public AdminContentController(AdminContentService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<AdminContentResponse>> list(
            @RequestParam(value = "contentType", required = false) String contentType) {
        return ApiResponse.ok(service.list(contentType));
    }

    @PatchMapping("/{contentType}")
    public ApiResponse<AdminContentResponse> update(@PathVariable("contentType") String contentType,
                                                    @RequestBody ContentUpdateRequest request) {
        return ApiResponse.ok(service.update(contentType, request), "안내 내용을 수정했어요.");
    }
}
