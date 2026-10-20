package com.fortunaball.record.controller;

import com.fortunaball.common.response.ApiResponse;
import com.fortunaball.record.dto.WinningRecordPublicRequest;
import com.fortunaball.record.dto.WinningRecordPublicResponse;
import com.fortunaball.record.service.AdminWinningRecordService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 당첨 기록 공개 설정 (API-27, 관리자). recordId 는 winning_verification.id 이다.
 * ⚠ 관리자 로그인 전이라 지금은 누구나 부를 수 있다. (AdminVerificationController 와 같은 주의사항: 로그인 붙이기 전에는 외부 공개 금지)
 */
@RestController
@RequestMapping("/api/admin/winning-records")
public class AdminWinningRecordController {

    private final AdminWinningRecordService service;

    public AdminWinningRecordController(AdminWinningRecordService service) {
        this.service = service;
    }

    @PatchMapping("/{recordId}")
    public ApiResponse<WinningRecordPublicResponse> setPublic(@PathVariable("recordId") Long recordId,
                                                              @Valid @RequestBody WinningRecordPublicRequest request) {
        return ApiResponse.ok(service.setPublic(recordId, request.publicVisible()));
    }
}
