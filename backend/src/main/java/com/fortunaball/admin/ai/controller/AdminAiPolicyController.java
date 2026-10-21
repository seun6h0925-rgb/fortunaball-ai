package com.fortunaball.admin.ai.controller;

import com.fortunaball.admin.ai.dto.AiPolicyCreateRequest;
import com.fortunaball.admin.ai.dto.AiPolicyItem;
import com.fortunaball.admin.ai.dto.AiPolicyUpdateRequest;
import com.fortunaball.admin.ai.service.AdminAiPolicyService;
import com.fortunaball.common.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 입력 정책 관리 (관리자). API-54 목록, API-55 등록, API-56 수정·비활성화.
 * 로그인(관리자 세션)과 CSRF 헤더(X-XSRF-TOKEN)가 필요하다. 바꾼 내용은 서비스에 최대 1분 안에 반영된다.
 */
@RestController
@RequestMapping("/api/admin/ai/policies")
public class AdminAiPolicyController {

    private final AdminAiPolicyService service;

    public AdminAiPolicyController(AdminAiPolicyService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<AiPolicyItem>> list(
            @RequestParam(value = "featureScope", required = false) String featureScope,
            @RequestParam(value = "active", required = false) Boolean active) {
        return ApiResponse.ok(service.list(featureScope, active));
    }

    @PostMapping
    public ApiResponse<AiPolicyItem> create(@RequestBody AiPolicyCreateRequest request) {
        return ApiResponse.ok(service.create(request), "정책을 등록했어요. 서비스에는 최대 1분 안에 반영돼요.");
    }

    @PatchMapping("/{policyId}")
    public ApiResponse<AiPolicyItem> update(@PathVariable("policyId") long policyId,
                                            @RequestBody AiPolicyUpdateRequest request) {
        return ApiResponse.ok(service.update(policyId, request), "정책을 수정했어요. 서비스에는 최대 1분 안에 반영돼요.");
    }
}
