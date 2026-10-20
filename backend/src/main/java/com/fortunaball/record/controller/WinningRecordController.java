package com.fortunaball.record.controller;

import com.fortunaball.common.response.ApiResponse;
import com.fortunaball.record.dto.WinningRecordListResponse;
import com.fortunaball.record.service.WinningRecordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 우리 서비스 당첨 기록 조회 (API-17). 로그인 없이 누구나 본다. (공개된 기록만)
 * 예: /api/winning-records?roundNo=1239&rank=4&page=0&size=20
 */
@RestController
@RequestMapping("/api/winning-records")
public class WinningRecordController {

    private final WinningRecordService service;

    public WinningRecordController(WinningRecordService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<WinningRecordListResponse> list(
            @RequestParam(value = "roundNo", required = false) Integer roundNo,
            @RequestParam(value = "rank", required = false) Integer rank,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(roundNo, rank, page, size));
    }
}
