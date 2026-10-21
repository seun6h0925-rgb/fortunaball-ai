package com.fortunaball.analyst.service;

import com.fortunaball.analyst.domain.AnalystEvidence;
import com.fortunaball.analyst.domain.EvidenceItem;
import com.fortunaball.analyst.domain.QuestionType;
import com.fortunaball.store.dto.StoreItem;
import com.fortunaball.store.service.StoreQueryService.AiSort;
import com.fortunaball.store.service.StoreQueryService.AiStoreResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 판매점 조회 결과를 AI 에게 줄 근거(AnalystEvidence)로 바꾼다. 화면의 "분석 근거 보기"에도 그대로 쓰인다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 위도·경도는 근거에 넣지 않는다. 이름, 주소, 당첨 횟수, 최근 횟수, 루키 명당 여부, (내 주변이면) 거리만 담는다.
 *  - 최근 횟수와 루키 명당은 최신 추첨일 기준 최근 N개월이다. 기준일만 함께 담고, 집계 시작 회차는 근거에 적지 않는다.
 *    (데이터 범위 안내는 서비스 안내 화면에서 한다)
 *  - 마지막 항목은 루키 명당 기준 설명이다. ("루키 명당이 뭐예요?"에 서버가 가진 기준값으로 답하게 한다)
 */
@Component
public class AnalystStoreEvidence {

    static final String REFERENCE = "동행복권 판매점별 당첨 이력";

    /** 지역·검색어 조회 결과 → 근거. 조건에 맞는 판매점이 없으면 null */
    public AnalystEvidence ofSearch(AiStoreResult r, String scope, AiSort sort, boolean rookieOnly) {
        if (r.items().isEmpty()) {
            return null;
        }
        String order = rookieOnly ? "루키 명당 (최근 당첨 많은 순)" : sort == AiSort.RECENT ? "최근 당첨 많은 순"
                : sort == AiSort.FIRST ? "1등 많은 순" : "1·2등 합계 많은 순";
        String basis = (scope.isBlank() ? "전국" : scope) + " · " + order
                + (r.total() == null ? "" : " · 조건에 맞는 " + r.total() + "곳 중 상위 " + r.items().size() + "곳")
                + " · " + dataNote(r);
        return new AnalystEvidence(QuestionType.MAP_STORE, basis, items(r, false), List.of(REFERENCE), true);
    }

    /** 내 주변 조회 결과 → 근거. 반경 안에 판매점이 없으면 null */
    public AnalystEvidence ofNearby(AiStoreResult r, int radiusMeters) {
        if (r.items().isEmpty()) {
            return null;
        }
        String basis = "내 위치 반경 " + radiusMeters + "m · 가까운 순"
                + (r.total() == null ? "" : " · 반경 안 " + r.total() + "곳 중 가까운 " + r.items().size() + "곳")
                + " · " + dataNote(r);
        return new AnalystEvidence(QuestionType.MAP_STORE, basis, items(r, true), List.of(REFERENCE), true);
    }

    private static List<EvidenceItem> items(AiStoreResult r, boolean withDistance) {
        List<EvidenceItem> list = new ArrayList<>();
        int no = 1;
        for (StoreItem s : r.items()) {
            list.add(new EvidenceItem(no++ + ". " + s.name() + regionOf(s), describe(s, withDistance)));
        }
        list.add(new EvidenceItem("루키 명당 기준", "최근 " + r.rookiePeriodMonths() + "개월 안에 1등 " + r.rookieMinFirst()
                + "회 이상 또는 2등 " + r.rookieMinSecond() + "회 이상 당첨된 판매점"));
        return list;
    }

    /** 예) 1등 3회(자동2·수동1·반자동0), 2등 5회 · 최근 1년 1등 1회·2등 0회 · 루키 명당 · 230m · 서울 강남구 ... */
    static String describe(StoreItem s, boolean withDistance) {
        StringBuilder sb = new StringBuilder();
        sb.append("1등 ").append(s.firstTotal()).append("회(자동").append(s.firstAuto()).append("·수동").append(s.firstManual())
                .append("·반자동").append(s.firstSemi()).append("), 2등 ").append(s.second()).append("회");
        sb.append(" · 최근 1등 ").append(s.recentFirst()).append("회·2등 ").append(s.recentSecond()).append("회");
        if (s.rookie()) {
            sb.append(" · 루키 명당");
        }
        if (withDistance && s.distanceMeters() != null) {
            sb.append(" · ").append(s.distanceMeters()).append("m");
        }
        if (s.roadAddress() != null && !s.roadAddress().isBlank()) {
            sb.append(" · ").append(s.roadAddress());
        }
        return sb.toString();
    }

    private static String regionOf(StoreItem s) {
        String region = ((s.sido() == null ? "" : s.sido()) + " " + (s.sigungu() == null ? "" : s.sigungu())).trim();
        return region.isEmpty() ? "" : " (" + region + ")";
    }

    private static String dataNote(AiStoreResult r) {
        return r.baseDate() == null ? "" : r.baseDate() + " 기준";
    }
}
