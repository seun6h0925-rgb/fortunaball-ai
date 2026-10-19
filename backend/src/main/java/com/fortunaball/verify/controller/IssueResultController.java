package com.fortunaball.verify.controller;

import com.fortunaball.common.response.ApiResponse;
import com.fortunaball.verify.dto.IssueResultResponse;
import com.fortunaball.verify.service.IssueResultService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 당첨 검증 API.
 *  - GET /api/issues/{issueCode}/result  발급번호 당첨 검증 결과 (API-06, 발급코드만 필요, 조회 횟수 제한)
 *
 * 주소는 발급(issue) 쪽 /api/issues 아래에 있지만, 하는 일이 "당첨 검증"이라서 verify 패키지에 둔다.
 * 컨트롤러는 입력을 받아 서비스에 넘기고 결과를 감싸서 돌려주는 일만 한다.
 */
@RestController
@RequestMapping("/api/issues")
public class IssueResultController {

    private final IssueResultService service;

    public IssueResultController(IssueResultService service) {
        this.service = service;
    }

    @GetMapping("/{issueCode}/result")
    public ApiResponse<IssueResultResponse> result(@PathVariable("issueCode") String issueCode,
                                                   HttpServletRequest request) {
        return ApiResponse.ok(service.getResult(issueCode, request.getRemoteAddr()));
    }
}
