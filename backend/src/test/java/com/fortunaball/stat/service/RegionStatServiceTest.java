package com.fortunaball.stat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.stat.dto.RegionStatResponse;
import com.fortunaball.stat.repository.RegionStatRepository;
import com.fortunaball.stat.repository.RegionStatRepository.RegionRow;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** DB 없이 가짜로 지역별 통계의 범위 결정, 앞 구간 비교, 증감률 계산을 확인한다. */
class RegionStatServiceTest {

    private final DrawRoundRepository drawRepo = mock(DrawRoundRepository.class);
    private final RegionStatRepository regionRepo = mock(RegionStatRepository.class);
    private RegionStatService service;

    @BeforeEach
    void setUp() {
        service = new RegionStatService(drawRepo, regionRepo);
    }

    private static DrawRound round(int no) {
        return new DrawRound(no, LocalDate.of(2026, 1, 1).plusWeeks(no), List.of(1, 2, 3, 4, 5, 6), 7, "TEST");
    }

    private static BusinessException reject(Runnable r) {
        return assertThrows(BusinessException.class, r::run);
    }

    @Test
    void 범위를_안_주면_최근_20회와_바로_앞_20회를_비교한다() {
        when(drawRepo.findTopByOrderByRoundNoDesc()).thenReturn(Optional.of(round(300)));
        when(regionRepo.firstHistoryRound()).thenReturn(262);
        when(regionRepo.sumByRegion(281, 300)).thenReturn(List.of(
                new RegionRow("서울", 8, 12, 20), new RegionRow("경기", 6, 6, 9)));

        RegionStatResponse r = service.regions(null, null);

        assertEquals(281, r.fromRound());
        assertEquals(300, r.toRound());
        // 앞 구간 261~280 은 자료 시작(262)보다 앞서므로 비교하지 않는다
        assertFalse(r.previousAvailable());
        assertNull(r.regions().get(0).firstChangeRate());
        verify(regionRepo, never()).sumByRegion(261, 280);
    }

    @Test
    void 증감률은_앞_구간_1등_건수_대비_퍼센트다() {
        when(regionRepo.firstHistoryRound()).thenReturn(262);
        when(regionRepo.sumByRegion(1001, 1020)).thenReturn(List.of(
                new RegionRow("서울", 9, 13, 20), new RegionRow("경기", 7, 6, 9), new RegionRow("제주", 1, 1, 0)));
        when(regionRepo.sumByRegion(981, 1000)).thenReturn(List.of(
                new RegionRow("서울", 8, 10, 15), new RegionRow("경기", 7, 8, 8)));

        RegionStatResponse r = service.regions(1001, 1020);

        assertTrue(r.previousAvailable());
        assertEquals(981, r.previousFromRound());
        assertEquals(1000, r.previousToRound());
        assertEquals("서울", r.regions().get(0).sido());          // 1등 많은 순
        assertEquals(30.0, r.regions().get(0).firstChangeRate());  // (13-10)/10
        assertEquals(10, r.regions().get(0).previousFirstCount());
        assertEquals(-25.0, r.regions().get(1).firstChangeRate()); // (6-8)/8
        assertNull(r.regions().get(2).firstChangeRate());           // 제주: 앞 구간 0건 → 계산 불가
    }

    @Test
    void 증감률은_소수_첫째_자리까지_반올림한다() {
        assertEquals(33.3, RegionStatService.changeRate(true, 4, 3));
        assertEquals(-66.7, RegionStatService.changeRate(true, 1, 3));
        assertNull(RegionStatService.changeRate(true, 5, 0));
        assertNull(RegionStatService.changeRate(false, 5, 3));
    }

    @Test
    void 일등_건수가_같으면_시도_이름_순으로_나온다() {
        when(regionRepo.firstHistoryRound()).thenReturn(262);
        when(regionRepo.sumByRegion(1001, 1020)).thenReturn(List.of(
                new RegionRow("부산", 3, 4, 0), new RegionRow("경기", 3, 4, 0)));
        when(regionRepo.sumByRegion(981, 1000)).thenReturn(List.of());

        RegionStatResponse r = service.regions(1001, 1020);

        assertEquals("경기", r.regions().get(0).sido());
        assertEquals("부산", r.regions().get(1).sido());
    }

    @Test
    void 회차_범위는_둘_다_줘야_한다() {
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.regions(1, null)).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.regions(null, 5)).getErrorCode());
    }

    @Test
    void 시작이_끝보다_크거나_200회를_넘으면_400() {
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.regions(10, 5)).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.regions(0, 5)).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, reject(() -> service.regions(1, 201)).getErrorCode());
    }

    @Test
    void 회차_자료가_없으면_503() {
        when(drawRepo.findTopByOrderByRoundNoDesc()).thenReturn(Optional.empty());

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, reject(() -> service.regions(null, null)).getErrorCode());
    }
}
