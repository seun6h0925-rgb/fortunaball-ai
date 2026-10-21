package com.fortunaball.admin.dashboard.controller;

import com.fortunaball.admin.dashboard.dto.AdminDashboardResponse;
import com.fortunaball.admin.dashboard.dto.UsageStatResponse;
import com.fortunaball.admin.dashboard.service.AdminDashboardService;
import com.fortunaball.admin.dashboard.service.AdminUsageStatService;
import com.fortunaball.common.response.ApiResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 대시보드(API-22)와 서비스 이용 통계(API-61). 날짜는 yyyy-MM-dd. */
@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;
    private final AdminUsageStatService usageStatService;

    public AdminDashboardController(AdminDashboardService dashboardService, AdminUsageStatService usageStatService) {
        this.dashboardService = dashboardService;
        this.usageStatService = usageStatService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardResponse> dashboard() {
        return ApiResponse.ok(dashboardService.dashboard());
    }

    @GetMapping("/statistics/usage")
    public ApiResponse<UsageStatResponse> usage(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(usageStatService.usage(from, to));
    }
}
