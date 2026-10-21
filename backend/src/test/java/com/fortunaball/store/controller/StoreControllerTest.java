package com.fortunaball.store.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.exception.GlobalExceptionHandler;
import com.fortunaball.store.dto.StoreDetailResponse;
import com.fortunaball.store.dto.StoreItem;
import com.fortunaball.store.dto.StoreListResponse;
import com.fortunaball.store.dto.StoreNearbyResponse;
import com.fortunaball.store.service.StoreQueryService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 판매점 API 3개의 입출력과 주소 연결 확인 (DB·서버 없이 컨트롤러만). */
class StoreControllerTest {

    private StoreQueryService service;
    private MockMvc mvc;

    private static StoreItem item(long id, Integer distance) {
        return new StoreItem(id, "가게", "서울 중구 a길 1", "", "서울", "중구", 37.5, 127.0,
                1, 0, 0, 1, 2, 3, 1, 0, true, distance);
    }

    @BeforeEach
    void setUp() {
        service = mock(StoreQueryService.class);
        mvc = MockMvcBuilders.standaloneSetup(new StoreController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 지역별_조회는_공통_성공_형식으로_돌려준다() throws Exception {
        when(service.search("서울", "중구", "복권", 0, 20)).thenReturn(
                new StoreListResponse(List.of(item(1, null)), 0, 20, 1, 1, null, 262));

        mvc.perform(get("/api/stores").param("sido", "서울").param("sigungu", "중구")
                        .param("keyword", "복권").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].storeId").value(1))
                .andExpect(jsonPath("$.data.items[0].rookie").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.statsFromRound").value(262));
    }

    @Test
    void nearby_주소는_상세가_아니라_내_주변_조회로_연결된다() throws Exception {
        when(service.nearby(37.5663, 126.9779, 500)).thenReturn(
                new StoreNearbyResponse(List.of(item(1, 230)), 500, null, 262));

        mvc.perform(get("/api/stores/nearby").param("lat", "37.5663").param("lng", "126.9779").param("radius", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].distanceMeters").value(230))
                .andExpect(jsonPath("$.data.radiusMeters").value(500));
    }

    @Test
    void 상세_조회() throws Exception {
        when(service.detail(5)).thenReturn(new StoreDetailResponse(item(5, null), List.of(), null, 262));

        mvc.perform(get("/api/stores/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.store.storeId").value(5))
                .andExpect(jsonPath("$.data.history").isArray());
    }

    @Test
    void 입력_오류는_400_공통_오류_형식() throws Exception {
        when(service.nearby(null, null, null)).thenThrow(
                new BusinessException(ErrorCode.INVALID_INPUT, "위치 정보를 확인해 주세요.", "위치값 오류"));

        mvc.perform(get("/api/stores/nearby"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON-001"));
    }

    @Test
    void 없는_판매점은_404_공통_오류_형식() throws Exception {
        when(service.detail(99)).thenThrow(
                new BusinessException(ErrorCode.NOT_FOUND, "판매점 정보를 찾을 수 없어요.", "없음"));

        mvc.perform(get("/api/stores/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMMON-002"));
    }
}
