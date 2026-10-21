package com.fortunaball.store.service;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.store.dto.StoreDetailResponse;
import com.fortunaball.store.dto.StoreItem;
import com.fortunaball.store.dto.StoreListResponse;
import com.fortunaball.store.dto.StoreNearbyResponse;
import com.fortunaball.store.geo.GeoDistance;
import com.fortunaball.store.repository.StoreQueryRepository;
import com.fortunaball.store.repository.StoreQueryRepository.RecentCount;
import com.fortunaball.store.repository.StoreQueryRepository.StoreRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 판매점 조회 (메뉴 7 "로또 당첨 판매점 지도"): 지역별(API-12), 내 주변(API-13), 상세(API-14).
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 폐점 판매점은 어디에도 나오지 않는다 (저장소 쿼리에서 is_closed = 0 만 읽는다). 상세 조회도 폐점이면 404.
 *  - 루키 명당의 "최근 1년" 기준일은 최신 확정 회차의 추첨일(baseDate)이다. 오늘 날짜가 아니다.
 *    기간·횟수는 설정값: fortuna.store.rookie.period-months(12), min-first(1), min-second(5)
 *  - 사용자의 위치(lat, lng)는 거리 계산에만 쓰고 저장·로그·응답에 담지 않는다.
 *    그래서 오류 메시지의 detail 에도 위치값을 넣지 않는다 (로그에 남을 수 있기 때문).
 *  - 반경은 300, 500, 1000(미터)만 허용한다.
 *  - 오류 구분: 입력이 잘못됨 → 400 COMMON-001, 판매점이 없거나 폐점 → 404 COMMON-002
 */
@Service
public class StoreQueryService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;
    static final int MIN_KEYWORD_LENGTH = 2;
    static final int MAX_KEYWORD_LENGTH = 50;
    static final Set<Integer> RADII_METERS = Set.of(300, 500, 1000);

    private final StoreQueryRepository repository;
    private final RookieRule rookieRule;
    private final int rookiePeriodMonths;

    public StoreQueryService(StoreQueryRepository repository,
                             @Value("${fortuna.store.rookie.period-months:12}") int rookiePeriodMonths,
                             @Value("${fortuna.store.rookie.min-first:1}") int rookieMinFirst,
                             @Value("${fortuna.store.rookie.min-second:5}") int rookieMinSecond) {
        this.repository = repository;
        this.rookiePeriodMonths = rookiePeriodMonths;
        this.rookieRule = new RookieRule(rookieMinFirst, rookieMinSecond);
    }

    /** API-12: 지역·검색어 조회 (총 당첨 횟수 많은 순, 쪽 나누기) */
    public StoreListResponse search(String sido, String sigungu, String keyword, Integer page, Integer size) {
        int pageNo = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageNo < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw invalid("쪽 번호와 크기를 확인해 주세요.", "page=" + pageNo + ", size=" + pageSize);
        }
        String cleanKeyword = blankToNull(keyword);
        if (cleanKeyword != null
                && (cleanKeyword.length() < MIN_KEYWORD_LENGTH || cleanKeyword.length() > MAX_KEYWORD_LENGTH)) {
            throw invalid("검색어는 " + MIN_KEYWORD_LENGTH + "글자 이상 " + MAX_KEYWORD_LENGTH + "글자 이하로 입력해 주세요.",
                    "keyword 길이 오류");
        }

        StoreQueryRepository.StorePage result = repository.search(
                blankToNull(sido), blankToNull(sigungu), cleanKeyword, pageNo, pageSize);

        LocalDate baseDate = repository.latestDrawDate();
        Map<Long, RecentCount> recent = repository.recentCounts(
                result.rows().stream().map(StoreRow::id).toList(), recentFrom(baseDate));
        List<StoreItem> items = result.rows().stream().map(r -> toItem(r, recent.get(r.id()), null)).toList();

        int totalPages = (int) ((result.total() + pageSize - 1) / pageSize);
        return new StoreListResponse(items, pageNo, pageSize, result.total(), totalPages, baseDate,
                repository.firstStatsRound());
    }

    /** API-13: 내 주변 조회 (가까운 순). 위치는 저장·기록하지 않는다. */
    public StoreNearbyResponse nearby(Double lat, Double lng, Integer radiusMeters) {
        if (lat == null || lng == null || lat < -90 || lat > 90 || lng < -180 || lng > 180
                || lat.isNaN() || lng.isNaN()) {
            throw invalid("위치 정보를 확인해 주세요.", "위치값 오류");   // 위치값은 detail 에도 넣지 않는다
        }
        if (radiusMeters == null || !RADII_METERS.contains(radiusMeters)) {
            throw invalid("반경은 300m, 500m, 1km 중에서 골라 주세요.", "radius=" + radiusMeters);
        }

        GeoDistance.Box box = GeoDistance.boundingBox(lat, lng, radiusMeters);
        record Near(StoreRow row, int meters) { }
        List<Near> near = repository.findInBox(box.minLat(), box.maxLat(), box.minLng(), box.maxLng()).stream()
                .map(r -> new Near(r, (int) Math.round(GeoDistance.meters(lat, lng, r.latitude(), r.longitude()))))
                .filter(n -> n.meters() <= radiusMeters)
                .sorted(Comparator.comparingInt(Near::meters).thenComparingLong(n -> n.row().id()))
                .toList();

        LocalDate baseDate = repository.latestDrawDate();
        Map<Long, RecentCount> recent = repository.recentCounts(
                near.stream().map(n -> n.row().id()).toList(), recentFrom(baseDate));
        List<StoreItem> items = near.stream()
                .map(n -> toItem(n.row(), recent.get(n.row().id()), n.meters())).toList();
        return new StoreNearbyResponse(items, radiusMeters, baseDate, repository.firstStatsRound());
    }

    /** API-14: 판매점 상세 (폐점이거나 없으면 404) */
    public StoreDetailResponse detail(long storeId) {
        StoreRow row = repository.findOpenById(storeId).orElseThrow(() -> new BusinessException(
                ErrorCode.NOT_FOUND, "판매점 정보를 찾을 수 없어요.", "storeId=" + storeId + " 없음 또는 폐점"));

        LocalDate baseDate = repository.latestDrawDate();
        Map<Long, RecentCount> recent = repository.recentCounts(List.of(row.id()), recentFrom(baseDate));
        return new StoreDetailResponse(toItem(row, recent.get(row.id()), null), repository.history(row.id()),
                baseDate, repository.firstStatsRound());
    }

    /** 루키 명당 "최근" 의 시작 기준 (이 날짜보다 뒤의 당첨만 센다). 기준일이 없으면 null. */
    private LocalDate recentFrom(LocalDate baseDate) {
        return baseDate == null ? null : baseDate.minusMonths(rookiePeriodMonths);
    }

    private StoreItem toItem(StoreRow r, RecentCount recent, Integer distanceMeters) {
        int recentFirst = recent == null ? 0 : recent.first();
        int recentSecond = recent == null ? 0 : recent.second();
        int firstTotal = r.firstAuto() + r.firstManual() + r.firstSemi();
        return new StoreItem(r.id(), r.name(), r.roadAddress(), r.jibunAddress(), r.sido(), r.sigungu(),
                r.latitude(), r.longitude(),
                r.firstAuto(), r.firstManual(), r.firstSemi(), firstTotal, r.second(), firstTotal + r.second(),
                recentFirst, recentSecond, rookieRule.isRookie(recentFirst, recentSecond), distanceMeters);
    }

    private static BusinessException invalid(String userMessage, String detail) {
        return new BusinessException(ErrorCode.INVALID_INPUT, userMessage, detail);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
