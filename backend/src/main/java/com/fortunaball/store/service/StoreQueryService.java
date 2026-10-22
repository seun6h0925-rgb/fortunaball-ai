package com.fortunaball.store.service;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.store.dto.StoreDetailResponse;
import com.fortunaball.store.dto.StoreItem;
import com.fortunaball.store.dto.StoreListResponse;
import com.fortunaball.store.dto.RookieStoreResponse;
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
 * 판매점 조회 (메뉴 7 "로또 당첨 판매점 지도"): 지역별(API-12), 내 주변(API-13), 상세(API-14), 루키 명당(API-66),
 * 그리고 AI 애널리스트용 조회(searchForAi).
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
    static final int MAX_AI_LIMIT = 10;
    static final int DEFAULT_ROOKIE_LIMIT = 4;   // 홈 화면 루키 명당 카드는 4곳
    static final int MAX_ROOKIE_LIMIT = 10;

    /** 시도 이름을 저장된 짧은 이름으로 바꾸는 표. (AI 가 "서울특별시"처럼 길게 말해도 찾을 수 있게 한다) */
    private static final Map<String, String> SIDO_SHORT = Map.ofEntries(
            Map.entry("서울특별시", "서울"), Map.entry("부산광역시", "부산"), Map.entry("대구광역시", "대구"),
            Map.entry("인천광역시", "인천"), Map.entry("광주광역시", "광주"), Map.entry("대전광역시", "대전"),
            Map.entry("울산광역시", "울산"), Map.entry("세종특별자치시", "세종"), Map.entry("경기도", "경기"),
            Map.entry("강원특별자치도", "강원"), Map.entry("강원도", "강원"), Map.entry("충청북도", "충북"),
            Map.entry("충청남도", "충남"), Map.entry("전북특별자치도", "전북"), Map.entry("전라북도", "전북"),
            Map.entry("전라남도", "전남"), Map.entry("경상북도", "경북"), Map.entry("경상남도", "경남"),
            Map.entry("제주특별자치도", "제주"));

    /**
     * AI 애널리스트가 받는 조회 결과.
     *  total   조건에 맞는 전체 판매점 수 (최근 당첨 정렬로 찾았을 때는 알 수 없어서 null)
     *  rookiePeriodMonths, rookieMinFirst, rookieMinSecond  루키 명당 기준 (답변에 기준을 함께 설명하기 위해 담는다)
     */
    /** AI 조회의 정렬: TOTAL = 1·2등 합계 많은 순, FIRST = 1등 많은 순, RECENT = 최근 당첨이 많은 순(최근 당첨이 있는 곳만) */
    public enum AiSort { TOTAL, FIRST, RECENT }

    public record AiStoreResult(List<StoreItem> items, Long total, LocalDate baseDate, Integer statsFromRound,
                                int rookiePeriodMonths, int rookieMinFirst, int rookieMinSecond) { }

    private final StoreQueryRepository repository;
    private final RookieRule rookieRule;
    private final int rookiePeriodMonths;
    private final int rookieMinFirst;
    private final int rookieMinSecond;

    public StoreQueryService(StoreQueryRepository repository,
                             @Value("${fortuna.store.rookie.period-months:12}") int rookiePeriodMonths,
                             @Value("${fortuna.store.rookie.min-first:1}") int rookieMinFirst,
                             @Value("${fortuna.store.rookie.min-second:5}") int rookieMinSecond) {
        this.repository = repository;
        this.rookiePeriodMonths = rookiePeriodMonths;
        this.rookieMinFirst = rookieMinFirst;
        this.rookieMinSecond = rookieMinSecond;
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

    /**
     * API-66: 루키 명당 조회 (홈 화면 "루키 명당" 카드용). 전국에서 루키 기준에 맞는 판매점을
     * 최근 1등 → 최근 2등 → 총 당첨 많은 순으로 limit 곳 준다. (AI 용 searchForAi 와 같은 저장소 조회를 쓴다)
     *
     * ⚠ 기준(최근 기간·1등/2등 횟수)은 설정값이라 응답에 함께 담는다. 화면이 기준을 그대로 안내하기 위해서다.
     * ⚠ limit 은 1~10, 안 주면 4. 범위를 벗어나면 400 (조용히 잘라 주지 않는다)
     */
    public RookieStoreResponse rookies(Integer limit) {
        int size = limit == null ? DEFAULT_ROOKIE_LIMIT : limit;
        if (size < 1 || size > MAX_ROOKIE_LIMIT) {
            throw invalid("조회 개수는 1~" + MAX_ROOKIE_LIMIT + "곳 사이로 입력해 주세요.", "limit=" + size);
        }
        LocalDate baseDate = repository.latestDrawDate();
        LocalDate from = recentFrom(baseDate);
        List<StoreRow> rows = from == null ? List.of()
                : repository.searchRecent(null, null, null, from, rookieMinFirst, rookieMinSecond, size);
        Map<Long, RecentCount> recent = repository.recentCounts(rows.stream().map(StoreRow::id).toList(), from);
        List<StoreItem> items = rows.stream().map(r -> toItem(r, recent.get(r.id()), null)).toList();
        return new RookieStoreResponse(items, baseDate, rookiePeriodMonths, rookieMinFirst, rookieMinSecond,
                repository.firstStatsRound());
    }

    /**
     * AI 애널리스트용 조회. 지역·검색어로 찾고 정렬을 고를 수 있다.
     *
     * @param sort        정렬 (AiSort). RECENT 는 "최근 1년 안에 당첨된 판매점"만 최근 당첨이 많은 순으로 준다.
     * @param rookieOnly  true 면 루키 명당 기준에 맞는 곳만 (정렬은 RECENT 로 본다)
     * @param limit       1~10 (넘으면 10으로 줄인다)
     * 좌표는 답에 쓰지 않지만, StoreItem 에는 들어 있으니 AI 에게 줄 때 빼는 것은 호출한 쪽(AnalystStoreEvidence)이 한다.
     */
    public AiStoreResult searchForAi(String sido, String sigungu, String keyword, AiSort sort, boolean rookieOnly,
                                     Integer limit) {
        int size = limit == null ? 5 : Math.min(MAX_AI_LIMIT, Math.max(1, limit));
        String cleanKeyword = blankToNull(keyword);
        if (cleanKeyword != null
                && (cleanKeyword.length() < MIN_KEYWORD_LENGTH || cleanKeyword.length() > MAX_KEYWORD_LENGTH)) {
            throw invalid("검색어는 " + MIN_KEYWORD_LENGTH + "글자 이상 " + MAX_KEYWORD_LENGTH + "글자 이하로 써야 해요.",
                    "keyword 길이 오류");
        }
        String cleanSido = normalizeSido(sido);
        String cleanSigungu = blankToNull(sigungu);
        LocalDate baseDate = repository.latestDrawDate();

        List<StoreRow> rows;
        Long total = null;
        AiSort order = sort == null ? AiSort.TOTAL : sort;
        if (order == AiSort.RECENT || rookieOnly) {
            LocalDate from = recentFrom(baseDate);
            rows = from == null ? List.of() : repository.searchRecent(cleanSido, cleanSigungu, cleanKeyword, from,
                    rookieOnly ? rookieMinFirst : null, rookieOnly ? rookieMinSecond : null, size);
        } else if (order == AiSort.FIRST) {
            rows = repository.searchByFirst(cleanSido, cleanSigungu, cleanKeyword, size);
        } else {
            StoreQueryRepository.StorePage page = repository.search(cleanSido, cleanSigungu, cleanKeyword, 0, size);
            rows = page.rows();
            total = page.total();
        }
        Map<Long, RecentCount> recent = repository.recentCounts(rows.stream().map(StoreRow::id).toList(), recentFrom(baseDate));
        List<StoreItem> items = rows.stream().map(r -> toItem(r, recent.get(r.id()), null)).toList();
        return new AiStoreResult(items, total, baseDate, repository.firstStatsRound(),
                rookiePeriodMonths, rookieMinFirst, rookieMinSecond);
    }

    /**
     * AI 애널리스트용 내 주변 조회 (가까운 순 상위 limit 곳). 위치는 서버 안에서만 쓰고 AI 에게 주지 않는다.
     * total 은 반경 안 전체 판매점 수다.
     */
    public AiStoreResult nearbyForAi(double lat, double lng, int radiusMeters, Integer limit) {
        int size = limit == null ? 5 : Math.min(MAX_AI_LIMIT, Math.max(1, limit));
        StoreNearbyResponse all = nearby(lat, lng, radiusMeters);
        return new AiStoreResult(all.items().stream().limit(size).toList(), (long) all.items().size(), all.baseDate(),
                all.statsFromRound(), rookiePeriodMonths, rookieMinFirst, rookieMinSecond);
    }

    /** "서울특별시" 같은 긴 이름은 저장된 짧은 이름("서울")으로 바꾼다. 이미 짧거나 모르는 이름은 그대로 둔다. */
    public static String normalizeSido(String sido) {
        String s = blankToNull(sido);
        return s == null ? null : SIDO_SHORT.getOrDefault(s, s);
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
