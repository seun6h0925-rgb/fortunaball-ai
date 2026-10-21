package com.fortunaball.admin.record.controller;

import com.fortunaball.admin.common.AdminPage;
import com.fortunaball.admin.record.dto.AdminWinningRecordItem;
import com.fortunaball.admin.record.service.AdminWinningRecordQueryService;
import com.fortunaball.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 우리 서비스 당첨 기록 관리 목록 (관리자, API-26).
 * 같은 주소의 PATCH(API-27, 공개 여부 변경)는 record 패키지의 AdminWinningRecordController 에 있다.
 */
@RestController
@RequestMapping("/api/admin/winning-records")
public class AdminWinningRecordListController {

    private final AdminWinningRecordQueryService service;

    public AdminWinningRecordListController(AdminWinningRecordQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<AdminPage<AdminWinningRecordItem>> list(
            @RequestParam(value = "roundNo", required = false) Integer roundNo,
            @RequestParam(value = "rank", required = false) Integer rank,
            @RequestParam(value = "publicVisible", required = false) Boolean publicVisible,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(service.list(roundNo, rank, publicVisible, page, size));
    }
}
