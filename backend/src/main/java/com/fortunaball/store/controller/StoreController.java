package com.fortunaball.store.controller;

import com.fortunaball.common.response.ApiResponse;
import com.fortunaball.store.dto.StoreDetailResponse;
import com.fortunaball.store.dto.StoreListResponse;
import com.fortunaball.store.dto.StoreNearbyResponse;
import com.fortunaball.store.service.StoreQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 판매점 조회 API (메뉴 7 "로또 당첨 판매점 지도")
 *
 *  - API-12  GET /api/stores                  지역별 조회   sido, sigungu, keyword, page(0~), size(1~100, 기본 20)
 *  - API-13  GET /api/stores/nearby           내 주변 조회  lat, lng, radius(300 | 500 | 1000)
 *  - API-14  GET /api/stores/{storeId}        상세 조회
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - /nearby 와 /{storeId} 는 모양이 겹치지만, Spring 은 글자가 정확히 맞는 주소(/nearby)를 먼저 고른다.
 *  - 내 주변 조회의 위치(lat, lng)는 사용자가 위치 사용 스위치를 켜고 동의한 경우에만 화면이 보낸다.
 *    서버는 저장·로그·AI 전달을 하지 않는다. 주소창(쿼리 문자열)에 들어가므로 접속 로그에 남지 않게 주의한다
 *    (요청 주소 전체를 기록하는 로그를 추가하지 말 것).
 *  - 숫자 변수는 Integer/Double 로 받아 서비스에서 검증한다 (빠졌을 때 공통 오류 형식 400 으로 응답하기 위해).
 *  - 로그인·게스트 식별값이 필요 없는 공개 API 다.
 */
@RestController
@RequestMapping("/api/stores")
public class StoreController {

    private final StoreQueryService storeQueryService;

    public StoreController(StoreQueryService storeQueryService) {
        this.storeQueryService = storeQueryService;
    }

    /** API-12: 지역별 조회 */
    @GetMapping
    public ApiResponse<StoreListResponse> search(
            @RequestParam(value = "sido", required = false) String sido,
            @RequestParam(value = "sigungu", required = false) String sigungu,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(storeQueryService.search(sido, sigungu, keyword, page, size));
    }

    /** API-13: 내 주변 조회 */
    @GetMapping("/nearby")
    public ApiResponse<StoreNearbyResponse> nearby(
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @RequestParam(value = "radius", required = false) Integer radius) {
        return ApiResponse.ok(storeQueryService.nearby(lat, lng, radius));
    }

    /** API-14: 상세 조회 */
    @GetMapping("/{storeId}")
    public ApiResponse<StoreDetailResponse> detail(@PathVariable("storeId") long storeId) {
        return ApiResponse.ok(storeQueryService.detail(storeId));
    }
}
