package com.fortunaball.admin.issue.controller;

import com.fortunaball.admin.common.AdminPage;
import com.fortunaball.admin.issue.dto.AdminIssueItem;
import com.fortunaball.admin.issue.dto.UnverifiedIssueItem;
import com.fortunaball.admin.issue.service.AdminIssueQueryService;
import com.fortunaball.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 번호 발급 기록 조회 (관리자). API-24 발급 기록 목록, API-48 미검증·검증 실패 건. */
@RestController
@RequestMapping("/api/admin/issues")
public class AdminIssueController {

    private final AdminIssueQueryService service;

    public AdminIssueController(AdminIssueQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<AdminPage<AdminIssueItem>> issues(
            @RequestParam(value = "roundNo", required = false) Integer roundNo,
            @RequestParam(value = "issueCode", required = false) String issueCode,
            @RequestParam(value = "guestToken", required = false) String guestToken,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(service.issues(roundNo, issueCode, guestToken, page, size));
    }

    @GetMapping("/unverified")
    public ApiResponse<AdminPage<UnverifiedIssueItem>> unverified(
            @RequestParam(value = "roundNo", required = false) Integer roundNo,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(service.unverified(roundNo, status, page, size));
    }
}
