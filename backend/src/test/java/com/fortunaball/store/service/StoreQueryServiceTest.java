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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.store.dto.StoreDetailResponse;
import com.fortunaball.store.dto.StoreListResponse;
import com.fortunaball.store.dto.StoreNearbyResponse;
import com.fortunaball.store.service.StoreQueryService.AiSort;
import com.fortunaball.store.service.StoreQueryService.AiStoreResult;
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

    // ---- AI 애널리스트용 조회 ----

    @Test
    void AI용_지역_조회는_총_당첨순이고_전체_건수를_알려준다() {
        when(repository.search("서울", "강남구", null, 0, 5))
                .thenReturn(new StorePage(List.of(row(1, LAT, 2, 1, 0, 3)), 120));
        when(repository.recentCounts(any(), eq(BASE.minusMonths(12)))).thenReturn(Map.of(1L, new RecentCount(1, 0)));

        AiStoreResult r = service.searchForAi("서울", "강남구", null, AiSort.TOTAL, false, null);

        assertEquals(1, r.items().size());
        assertTrue(r.items().get(0).rookie());
        assertEquals(120L, r.total());
        assertEquals(12, r.rookiePeriodMonths());
        assertEquals(1, r.rookieMinFirst());
        assertEquals(5, r.rookieMinSecond());
        assertEquals(BASE, r.baseDate());
    }

    @Test
    void AI용_시도_이름은_긴_이름도_짧은_이름으로_바꿔_찾는다() {
        when(repository.search(eq("서울"), any(), any(), anyInt(), anyInt())).thenReturn(new StorePage(List.of(), 0));
        when(repository.recentCounts(any(), any())).thenReturn(Map.of());

        service.searchForAi("서울특별시", null, null, AiSort.TOTAL, false, 5);

        verify(repository).search("서울", null, null, 0, 5);
        assertEquals("경기", StoreQueryService.normalizeSido("경기도"));
        assertEquals("전남광주", StoreQueryService.normalizeSido("전남광주"));   // 모르는 이름은 그대로
        assertNull(StoreQueryService.normalizeSido("  "));
    }

    @Test
    void AI용_조회의_개수는_1에서_10으로_맞춘다() {
        when(repository.search(any(), any(), any(), anyInt(), anyInt())).thenReturn(new StorePage(List.of(), 0));
        when(repository.recentCounts(any(), any())).thenReturn(Map.of());

        service.searchForAi(null, null, null, AiSort.TOTAL, false, 99);
        service.searchForAi(null, null, null, AiSort.TOTAL, false, 0);

        verify(repository).search(null, null, null, 0, 10);
        verify(repository).search(null, null, null, 0, 1);
    }

    @Test
    void AI용_최근_당첨_정렬은_기준일_이후만_세고_전체_건수는_모른다() {
        when(repository.searchRecent(eq("경기"), any(), any(), eq(BASE.minusMonths(12)), isNull(), isNull(), eq(5)))
                .thenReturn(List.of(row(2, LAT, 1, 0, 0, 0)));
        when(repository.recentCounts(any(), any())).thenReturn(Map.of(2L, new RecentCount(1, 0)));

        AiStoreResult r = service.searchForAi("경기", null, null, AiSort.RECENT, false, null);

        assertEquals(1, r.items().size());
        assertNull(r.total());
    }

    @Test
    void AI용_루키만_조회는_루키_기준_횟수를_저장소에_넘긴다() {
        when(repository.searchRecent(any(), any(), any(), any(), eq(1), eq(5), anyInt())).thenReturn(List.of());
        when(repository.recentCounts(any(), any())).thenReturn(Map.of());

        service.searchForAi(null, null, null, AiSort.TOTAL, true, null);

        verify(repository).searchRecent(null, null, null, BASE.minusMonths(12), 1, 5, 5);
    }

    @Test
    void AI용_검색어가_한_글자면_400() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.searchForAi(null, null, "가", AiSort.TOTAL, false, null));

        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
    }

    @Test
    void AI용_내_주변은_가까운_순_상위_N곳과_반경_안_전체_수를_준다() {
        when(repository.findInBox(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(
                row(3, LAT + 0.004, 0, 0, 0, 0),
                row(2, LAT + 0.003, 0, 0, 0, 0),
                row(1, LAT + 0.002, 0, 0, 0, 0)));
        when(repository.recentCounts(any(), any())).thenReturn(Map.of());

        AiStoreResult r = service.nearbyForAi(LAT, LNG, 500, 2);

        assertEquals(2, r.items().size());
        assertEquals(1L, r.items().get(0).storeId());
        assertEquals(3L, r.total());
    }

    @Test
    void AI용_1등_정렬은_1등_많은_순_조회를_쓰고_전체_건수는_모른다() {
        when(repository.searchByFirst("서울", "강남구", null, 5)).thenReturn(List.of(row(4, LAT, 3, 1, 0, 2)));
        when(repository.recentCounts(any(), any())).thenReturn(Map.of());

        AiStoreResult r = service.searchForAi("서울", "강남구", null, AiSort.FIRST, false, null);

        assertEquals(4, r.items().get(0).firstTotal());
        assertNull(r.total());
        verify(repository).searchByFirst("서울", "강남구", null, 5);
    }
}
