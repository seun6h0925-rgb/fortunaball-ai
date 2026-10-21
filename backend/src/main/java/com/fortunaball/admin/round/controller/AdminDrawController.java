package com.fortunaball.admin.round.controller;

import com.fortunaball.admin.round.dto.AdminDrawItem;
import com.fortunaball.admin.round.service.AdminDrawQueryService;
import com.fortunaball.common.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 회차 데이터·통계 상태 목록 (관리자, API-46). 재수집·재계산(API-23, 47)은 이후 같은 패키지에 추가한다. */
@RestController
@RequestMapping("/api/admin/draws")
public class AdminDrawController {

    private final AdminDrawQueryService service;

    public AdminDrawController(AdminDrawQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<AdminDrawItem>> list(
            @RequestParam(value = "fromRound", required = false) Integer fromRound,
            @RequestParam(value = "toRound", required = false) Integer toRound) {
        return ApiResponse.ok(service.list(fromRound, toRound));
    }
}
