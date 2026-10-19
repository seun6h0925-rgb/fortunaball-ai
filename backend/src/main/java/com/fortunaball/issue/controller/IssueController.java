package com.fortunaball.issue.controller;

import com.fortunaball.common.guest.GuestToken;
import com.fortunaball.common.response.ApiResponse;
import com.fortunaball.issue.dto.IssueConfirmRequest;
import com.fortunaball.issue.dto.IssueConfirmResponse;
import com.fortunaball.issue.service.IssueService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 번호 발급 API.
 *  - POST /api/issues   발급 확정 (헤더 X-Guest-Token 필요, 본문에 confirmToken)
 *
 * 컨트롤러는 입력을 받아 서비스에 넘기고 결과를 감싸서 돌려주는 일만 한다.
 * 이후 내역 조회(API-05/42)도 이 컨트롤러에 추가한다.
 */
@RestController
@RequestMapping("/api/issues")
public class IssueController {

    private final IssueService issueService;

    public IssueController(IssueService issueService) {
        this.issueService = issueService;
    }

    @PostMapping
    public ApiResponse<IssueConfirmResponse> confirm(GuestToken guest, @RequestBody IssueConfirmRequest request) {
        return ApiResponse.ok(issueService.confirm(guest, request));
    }
}
