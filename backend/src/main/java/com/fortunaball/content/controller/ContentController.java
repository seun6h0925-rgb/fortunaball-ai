package com.fortunaball.content.controller;

import com.fortunaball.common.response.ApiResponse;
import com.fortunaball.content.dto.ContentResponse;
import com.fortunaball.content.service.ContentQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 안내 콘텐츠 조회 API (메뉴 9 "당첨금 수령 가이드", 메뉴 10 "서비스 안내·유의사항")
 *
 *  - API-20  GET /api/contents/{contentType}
 *    contentType: SERVICE_INTRO, USAGE_GUIDE, PRIVACY, AI_NOTICE, NO_GUARANTEE,
 *                 GAMBLING_WARNING, DISCLAIMER, CLAIM_GUIDE, FAQ
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 로그인·게스트 식별값이 필요 없는 공개 API 다.
 *  - 글 수정 API(API-58, 59)는 관리자 로그인을 만든 뒤에 추가한다.
 *  - 컨트롤러는 받아서 서비스에 넘기고 공통 응답으로 감싸기만 한다.
 */
@RestController
@RequestMapping("/api/contents")
public class ContentController {

    private final ContentQueryService contentQueryService;

    public ContentController(ContentQueryService contentQueryService) {
        this.contentQueryService = contentQueryService;
    }

    /** API-20: 안내 콘텐츠 한 건 */
    @GetMapping("/{contentType}")
    public ApiResponse<ContentResponse> get(@PathVariable("contentType") String contentType) {
        return ApiResponse.ok(contentQueryService.getByType(contentType));
    }
}
