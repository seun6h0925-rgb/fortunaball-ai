package com.fortunaball.store.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.store.dto.StoreDetailResponse;
import com.fortunaball.store.dto.StoreListResponse;
import com.fortunaball.store.dto.StoreNearbyResponse;
import com.fortunaball.store.repository.StoreQueryRepository;
import com.fortunaball.store.repository.StoreQueryRepository.RecentCount;
import com.fortunaball.store.repository.StoreQueryRepository.StorePage;
import com.fortunaball.store.repository.StoreQueryRepository.StoreRow;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 판매점 조회 규칙 확인 (DB 없이 저장소를 가짜로 바꿔서 확인한다). */
class StoreQueryServiceTest {

    private static final double LAT = 37.5663;
    private static final double LNG = 126.9779;
    private static final LocalDate BASE = LocalDate.of(2026, 10, 17);

    private StoreQueryRepository repository;
    private StoreQueryService service;

    @BeforeEach
    void setUp() {
        repository = mock(StoreQueryRepository.class);
        service = new StoreQueryService(repository, 12, 1, 5);
        when(repository.latestDrawDate()).thenReturn(BASE);
        when(repository.firstStatsRound()).thenReturn(262);
    }

    private static StoreRow row(long id, double lat, int auto, int manual, int semi, int second) {
        return new StoreRow(id, "가게" + id, "서울 중구 a길 " + id, "", "서울", "중구", lat, LNG, auto, manual, semi, second);
    }

    // ---- API-12 ----

    @Test
    void 목록은_당첨_합계와_루키_여부와_쪽_정보를_채운다() {
        when(repository.search(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new StorePage(List.of(row(1, LAT, 2, 1, 0, 3), row(2, LAT, 0, 0, 0, 1)), 45));
        when(repository.recentCounts(any(), eq(BASE.minusMonths(12)))).thenReturn(Map.of(1L, new RecentCount(1, 0)));

        StoreListResponse res = service.search("서울", "중구", null, 0, 20);

        assertEquals(2, res.items().size());
        assertEquals(3, res.items().get(0).firstTotal());
        assertEquals(6, res.items().get(0).totalWins());   // 1등 3 + 2등 3
        assertTrue(res.items().get(0).rookie());           // 최근 1등 1회
        assertFalse(res.items().get(1).rookie());
        assertEquals(45, res.totalElements());
        assertEquals(3, res.totalPages());                 // 45건 / 20개씩
        assertEquals(BASE, res.baseDate());
        assertEquals(262, res.statsFromRound());
        assertNull(res.items().get(0).distanceMeters());
    }

    @Test
    void 쪽_크기가_100을_넘으면_400() {
        BusinessException e = assertThrows(BusinessException.class, () -> service.search(null, null, null, 0, 101));

        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
    }

    @Test
    void 검색어가_한_글자면_400() {
        BusinessException e = assertThrows(BusinessException.class, () -> service.search(null, null, "가", 0, 20));

        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
    }

    // ---- API-13 ----

    @Test
    void 내_주변은_반경_안만_가까운_순으로_돌려준다() {
        // 위도 0.002 ≈ 222m, 0.004 ≈ 445m, 0.01 ≈ 1112m (반경 500m 이면 앞의 둘만)
        when(repository.findInBox(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(
                row(3, LAT + 0.01, 0, 0, 0, 0),
                row(2, LAT + 0.004, 0, 0, 0, 0),
                row(1, LAT + 0.002, 0, 0, 0, 0)));

        StoreNearbyResponse res = service.nearby(LAT, LNG, 500);

        assertEquals(2, res.items().size());
        assertEquals(1L, res.items().get(0).storeId());
        assertEquals(2L, res.items().get(1).storeId());
        assertTrue(res.items().get(0).distanceMeters() > 200 && res.items().get(0).distanceMeters() < 245);
        assertEquals(500, res.radiusMeters());
    }

    @Test
    void 반경이_허용값이_아니면_400() {
        BusinessException e = assertThrows(BusinessException.class, () -> service.nearby(LAT, LNG, 400));

        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
    }

    @Test
    void 위치가_없으면_400이고_오류_정보에_위치값을_담지_않는다() {
        BusinessException e = assertThrows(BusinessException.class, () -> service.nearby(null, LNG, 500));

        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());

        BusinessException e2 = assertThrows(BusinessException.class, () -> service.nearby(95.0, 127.123456, 500));
        assertFalse(String.valueOf(e2.getDetail()).contains("127.123456"));
        assertFalse(String.valueOf(e2.getDetail()).contains("95"));
    }

    // ---- API-14 ----

    @Test
    void 상세는_판매점과_당첨_이력을_돌려준다() {
        when(repository.findOpenById(7)).thenReturn(Optional.of(row(7, LAT, 1, 0, 0, 0)));
        when(repository.history(7)).thenReturn(List.of());

        StoreDetailResponse res = service.detail(7);

        assertEquals(7L, res.store().storeId());
        assertEquals(1, res.store().firstAuto());
        assertEquals(BASE, res.baseDate());
    }

    @Test
    void 없거나_폐점한_판매점은_404() {
        when(repository.findOpenById(99)).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class, () -> service.detail(99));

        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
    }
}
