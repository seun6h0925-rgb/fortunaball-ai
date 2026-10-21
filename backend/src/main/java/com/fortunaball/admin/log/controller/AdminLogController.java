package com.fortunaball.admin.log.controller;

import com.fortunaball.admin.common.AdminPage;
import com.fortunaball.admin.log.dto.ErrorLogItem;
import com.fortunaball.admin.log.dto.SchedulerLogItem;
import com.fortunaball.admin.log.service.AdminLogQueryService;
import com.fortunaball.common.response.ApiResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시스템 운영 로그 조회 (관리자). API-38 오류 로그, API-39 스케줄러 실행 로그.
 * 날짜는 yyyy-MM-dd 형식이다. (예: ?from=2026-10-01&to=2026-10-21)
 */
@RestController
@RequestMapping("/api/admin")
public class AdminLogController {

    private final AdminLogQueryService service;

    public AdminLogController(AdminLogQueryService service) {
        this.service = service;
    }

    @GetMapping("/errors")
    public ApiResponse<AdminPage<ErrorLogItem>> errors(
            @RequestParam(value = "errorType", required = false) String errorType,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(service.errors(errorType, from, to, page, size));
    }

    @GetMapping("/scheduler/logs")
    public ApiResponse<AdminPage<SchedulerLogItem>> schedulerLogs(
            @RequestParam(value = "jobName", required = false) String jobName,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(service.schedulerLogs(jobName, from, to, page, size));
    }
}
