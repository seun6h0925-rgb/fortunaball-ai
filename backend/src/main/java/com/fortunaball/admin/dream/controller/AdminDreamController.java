package com.fortunaball.admin.dream.controller;

import com.fortunaball.admin.common.AdminPage;
import com.fortunaball.admin.dream.dto.DreamSymbolCreateRequest;
import com.fortunaball.admin.dream.dto.DreamSymbolItem;
import com.fortunaball.admin.dream.dto.DreamSymbolUpdateRequest;
import com.fortunaball.admin.dream.service.AdminDreamService;
import com.fortunaball.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 꿈 상징 관리 (관리자). API-57 목록, API-33 등록, API-34 수정·비활성화.
 * 로그인(관리자 세션)과 CSRF 헤더(X-XSRF-TOKEN)가 필요하다. 꿈 풀이는 요청마다 DB 를 읽으므로 바로 반영된다.
 */
@RestController
@RequestMapping("/api/admin/dream-symbols")
public class AdminDreamController {

    private final AdminDreamService service;

    public AdminDreamController(AdminDreamService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<AdminPage<DreamSymbolItem>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(service.list(keyword, source, active, page, size));
    }

    @PostMapping
    public ApiResponse<DreamSymbolItem> create(@RequestBody DreamSymbolCreateRequest request) {
        return ApiResponse.ok(service.create(request), "꿈 상징을 등록했어요.");
    }

    @PatchMapping("/{symbolId}")
    public ApiResponse<DreamSymbolItem> update(@PathVariable("symbolId") long symbolId,
                                               @RequestBody DreamSymbolUpdateRequest request) {
        return ApiResponse.ok(service.update(symbolId, request), "꿈 상징을 수정했어요.");
    }
}
