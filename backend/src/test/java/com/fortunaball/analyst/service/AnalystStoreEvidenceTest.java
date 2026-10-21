package com.fortunaball.analyst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fortunaball.analyst.domain.AnalystEvidence;
import com.fortunaball.analyst.domain.QuestionType;
import com.fortunaball.store.dto.StoreItem;
import com.fortunaball.store.service.StoreQueryService.AiSort;
import com.fortunaball.store.service.StoreQueryService.AiStoreResult;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 판매점 조회 결과를 AI 에게 줄 근거로 바꾸는 규칙 확인. */
class AnalystStoreEvidenceTest {

    private final AnalystStoreEvidence evidence = new AnalystStoreEvidence();

    private static StoreItem store(long id, boolean rookie, Integer distance) {
        return new StoreItem(id, "행운복권" + id, "서울 강남구 테헤란로 " + id, "", "서울", "강남구", 37.5001, 127.0001,
                2, 1, 0, 3, 4, 7, 1, 0, rookie, distance);
    }

    private static AiStoreResult result(List<StoreItem> items, Long total) {
        return new AiStoreResult(items, total, LocalDate.of(2026, 10, 17), 262, 12, 1, 5);
    }

    @Test
    void 지역_조회_근거에는_기준_횟수_루키_기준이_들어_있다() {
        AnalystEvidence e = evidence.ofSearch(result(List.of(store(1, true, null)), 120L), "서울 강남구", AiSort.TOTAL, false);

        assertEquals(QuestionType.MAP_STORE, e.type());
        assertTrue(e.basis().contains("서울 강남구"));
        assertTrue(e.basis().contains("1·2등 합계 많은 순"));
        assertTrue(e.basis().contains("120곳 중 상위 1곳"));
        assertFalse(e.basis().contains("262회"));   // 집계 시작 회차는 근거에 적지 않는다
        assertTrue(e.basis().contains("2026-10-17 기준"));
        String first = e.items().get(0).value();
        assertTrue(first.contains("1등 3회(자동2·수동1·반자동0)"));
        assertTrue(first.contains("2등 4회"));
        assertTrue(first.contains("루키 명당"));
        assertTrue(e.items().get(0).label().startsWith("1. 행운복권1 (서울 강남구)"));
        // 마지막 항목 = 루키 명당 기준 (설정값에서 온 숫자)
        assertEquals("루키 명당 기준", e.items().get(e.items().size() - 1).label());
        assertTrue(e.items().get(e.items().size() - 1).value().contains("12개월"));
    }

    @Test
    void 루키가_아니면_루키_표시가_없다() {
        AnalystEvidence e = evidence.ofSearch(result(List.of(store(1, false, null)), 1L), "", AiSort.TOTAL, false);

        assertFalse(e.items().get(0).value().contains("루키 명당"));
        assertTrue(e.basis().startsWith("전국"));
    }

    @Test
    void 최근_당첨_정렬이면_기준에_그렇게_적는다() {
        AnalystEvidence recent = evidence.ofSearch(result(List.of(store(1, true, null)), null), "경기", AiSort.RECENT, false);
        AnalystEvidence rookie = evidence.ofSearch(result(List.of(store(1, true, null)), null), "경기", AiSort.RECENT, true);

        assertTrue(recent.basis().contains("최근 당첨 많은 순"));
        assertTrue(rookie.basis().contains("루키 명당"));
        assertFalse(recent.basis().contains("곳 중 상위"));   // 전체 수를 모를 때는 적지 않는다
    }

    @Test
    void 일등_정렬이면_기준에_1등_많은_순이라고_적는다() {
        AnalystEvidence e = evidence.ofSearch(result(List.of(store(1, false, null)), null), "서울", AiSort.FIRST, false);

        assertTrue(e.basis().contains("1등 많은 순"));
    }

    @Test
    void 결과가_없으면_null() {
        assertNull(evidence.ofSearch(result(List.of(), 0L), "서울", AiSort.TOTAL, false));
        assertNull(evidence.ofNearby(result(List.of(), 0L), 500));
    }

    @Test
    void 내_주변_근거에는_거리가_있고_좌표는_없다() {
        AnalystEvidence e = evidence.ofNearby(result(List.of(store(1, false, 230)), 8L), 500);

        assertTrue(e.basis().contains("반경 500m"));
        assertTrue(e.basis().contains("8곳 중 가까운 1곳"));
        assertTrue(e.items().get(0).value().contains("230m"));
        String all = e.toPromptText();
        assertFalse(all.contains("37.5001"));
        assertFalse(all.contains("127.0001"));
    }
}
