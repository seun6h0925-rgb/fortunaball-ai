package com.fortunaball.admin.action.controller;

import com.fortunaball.admin.action.dto.JobRunResult;
import com.fortunaball.admin.action.dto.RecalculateResult;
import com.fortunaball.admin.action.dto.ResyncResult;
import com.fortunaball.admin.action.dto.VerifyResult;
import com.fortunaball.admin.action.service.AdminOperationService;
import com.fortunaball.common.response.ApiResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영 작업 실행 (관리자). API-23 회차 재수집, API-25 당첨 검증 재실행, API-40 스케줄러 수동 실행, API-47 통계 재계산.
 * 로그인(관리자 세션)과 CSRF 헤더(X-XSRF-TOKEN)가 필요하다. 모두 POST 이고 요청 본문은 없다.
 */
@RestController
public class AdminOperationController {

    private final AdminOperationService service;

    public AdminOperationController(AdminOperationService service) {
        this.service = service;
    }

    @PostMapping("/api/admin/draws/{round}/resync")
    public ApiResponse<ResyncResult> resync(@PathVariable("round") int round) {
        return ApiResponse.ok(service.resync(round), "회차 데이터를 공식 사이트에서 다시 확인했어요.");
    }

    @PostMapping("/api/admin/issues/verify")
    public ApiResponse<VerifyResult> verify() {
        VerifyResult result = service.verify();
        return ApiResponse.ok(result, result.message());
    }

    @PostMapping("/api/admin/scheduler/{jobName}/run")
    public ApiResponse<JobRunResult> runJob(@PathVariable("jobName") String jobName) {
        JobRunResult result = service.runJob(jobName);
        return ApiResponse.ok(result, result.message());
    }

    @PostMapping("/api/admin/statistics/{round}/recalculate")
    public ApiResponse<RecalculateResult> recalculate(@PathVariable("round") int round) {
        return ApiResponse.ok(service.recalculate(round), "통계를 다시 계산했어요.");
    }
}
