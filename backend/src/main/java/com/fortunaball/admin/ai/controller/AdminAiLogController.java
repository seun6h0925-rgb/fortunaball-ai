package com.fortunaball.admin.ai.controller;

import com.fortunaball.admin.ai.dto.AiLogResponse;
import com.fortunaball.admin.ai.service.AdminAiLogService;
import com.fortunaball.common.response.ApiResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 운영 로그 조회 (관리자, API-32).
 * featureType: ANALYST / DREAM, status: SUCCESS / FAILED / BLOCKED, 날짜는 yyyy-MM-dd
 */
@RestController
@RequestMapping("/api/admin/ai")
public class AdminAiLogController {

    private final AdminAiLogService service;

    public AdminAiLogController(AdminAiLogService service) {
        this.service = service;
    }

    @GetMapping("/logs")
    public ApiResponse<AiLogResponse> logs(
            @RequestParam(value = "featureType", required = false) String featureType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(service.logs(featureType, status, from, to, page, size));
    }
}
