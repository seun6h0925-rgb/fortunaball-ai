package com.fortunaball.issue.controller;

import com.fortunaball.common.guest.GuestToken;
import com.fortunaball.common.response.ApiResponse;
import com.fortunaball.issue.dto.IssueConfirmRequest;
import com.fortunaball.issue.dto.IssueConfirmResponse;
import com.fortunaball.issue.dto.IssueDetailResponse;
import com.fortunaball.issue.dto.IssueListResponse;
import com.fortunaball.issue.service.IssueQueryService;
import com.fortunaball.issue.service.IssueService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 번호 발급 API.
 *  - POST /api/issues              발급 확정 (API-04, 헤더 X-Guest-Token + 본문 confirmToken)
 *  - GET  /api/issues              이 기기의 발급 내역 목록 (API-42, 헤더 X-Guest-Token, 선택: page, size)
 *  - GET  /api/issues/{issueCode}  발급 내역 상세 (API-05, 발급코드만 필요, 조회 횟수 제한)
 *
 * 컨트롤러는 입력을 받아 서비스에 넘기고 결과를 감싸서 돌려주는 일만 한다.
 * ⚠ page/size 는 문자열로 받아 서비스에서 직접 검사한다. (숫자가 아닌 값도 우리 오류 형식으로 400 을 돌려주려고. StatController 와 같은 방식)
 */
@RestController
@RequestMapping("/api/issues")
public class IssueController {

    private final IssueService issueService;
    private final IssueQueryService queryService;

    public IssueController(IssueService issueService, IssueQueryService queryService) {
        this.issueService = issueService;
        this.queryService = queryService;
    }

    @PostMapping
    public ApiResponse<IssueConfirmResponse> confirm(GuestToken guest, @RequestBody IssueConfirmRequest request) {
        return ApiResponse.ok(issueService.confirm(guest, request));
    }

    @GetMapping
    public ApiResponse<IssueListResponse> list(GuestToken guest,
                                               @RequestParam(name = "page", required = false) String page,
                                               @RequestParam(name = "size", required = false) String size) {
        return ApiResponse.ok(queryService.getList(guest, page, size));
    }

    @GetMapping("/{issueCode}")
    public ApiResponse<IssueDetailResponse> detail(@PathVariable("issueCode") String issueCode,
                                                   HttpServletRequest request) {
        return ApiResponse.ok(queryService.getDetail(issueCode, request.getRemoteAddr()));
    }
}
