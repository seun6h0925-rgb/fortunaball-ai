package com.fortunaball.admin.store.service;

import com.fortunaball.admin.store.dto.DiffSummary;
import com.fortunaball.admin.store.dto.StoreDiffItem;
import com.fortunaball.store.importer.StoreAddressParser;
import com.fortunaball.store.importer.StoreCsvRow;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 판매점 파일과 현재 DB 를 견줘 신규·변경·당첨 이력 차이를 센다. (DB 를 직접 만지지 않는 순수 계산)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 판매점은 "판매점 번호(shop_code)"로 같은지 판단한다. 같은 판매점은 파일에서 여러 줄(회차마다) 나오지만 값은 같아서 첫 줄만 쓴다.
 *  - 비교 항목은 StoreImportService 가 실제로 덮어쓰는 항목과 같다: 판매점명, 도로명·지번 주소, 시도, 시군구, 위도, 경도, 폐점 여부.
 *    (비교 항목이 반영 항목보다 적으면 "변경 없음"으로 나왔는데 값이 바뀌는 일이 생긴다)
 *  - 좌표는 자릿수가 달라도(37.5 / 37.5000000) 같은 값으로 본다 (compareTo).
 *  - 당첨 이력은 (판매점, 회차, 등수, 구분)이 같은 줄끼리 짝을 짓고 개수를 센다. 같은 판매점이 한 회차에 2번 당첨될 수 있어서
 *    "있다/없다"가 아니라 "몇 개"로 견준다.
 *  - 이 파일 종류는 판매점을 삭제하지 않는다. (파일에 없다고 폐업은 아니다) 그래서 삭제 건수는 항상 0 이다.
 */
public final class StoreDiffCalculator {

    public static final String NEW = "NEW";
    public static final String CHANGED = "CHANGED";

    private StoreDiffCalculator() {
    }

    /** DB 에 있는 판매점 값 */
    public record ExistingStore(long id, String name, String roadAddress, String jibunAddress, String sido,
                                String sigungu, BigDecimal latitude, BigDecimal longitude, boolean closed) {
    }

    public record Diff(List<StoreDiffItem> items, DiffSummary summary) {
    }

    /** 당첨 이력 짝짓기 열쇠. 구분이 없으면(null) 빈 글자로 본다. */
    public static String historyKey(String shopCode, int round, int rank, String winType) {
        return shopCode + "|" + round + "|" + rank + "|" + (winType == null ? "" : winType);
    }

    public static Diff compute(List<StoreCsvRow> rows, Map<String, ExistingStore> existing,
                               Map<String, Integer> existingHistoryCounts) {
        Map<String, StoreCsvRow> firstRows = new LinkedHashMap<>();
        Map<String, Integer> fileHistoryCounts = new LinkedHashMap<>();
        for (StoreCsvRow row : rows) {
            firstRows.putIfAbsent(row.shopCode(), row);
            fileHistoryCounts.merge(historyKey(row.shopCode(), row.round(), row.rank(), row.winType()), 1, Integer::sum);
        }

        List<StoreDiffItem> items = new ArrayList<>();
        int newStores = 0;
        int changed = 0;
        int unchanged = 0;
        for (StoreCsvRow row : firstRows.values()) {
            Map<String, String> after = fileValues(row);
            ExistingStore old = existing.get(row.shopCode());
            if (old == null) {
                newStores++;
                items.add(new StoreDiffItem(NEW, row.shopCode(), row.storeName(), Map.of(), after));
                continue;
            }
            Map<String, String> before = existingValues(old);
            Map<String, String> beforeChanged = new LinkedHashMap<>();
            Map<String, String> afterChanged = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : after.entrySet()) {
                String key = e.getKey();
                if (!sameValue(key, before.get(key), e.getValue())) {
                    beforeChanged.put(key, before.get(key));
                    afterChanged.put(key, e.getValue());
                }
            }
            if (afterChanged.isEmpty()) {
                unchanged++;
            } else {
                changed++;
                items.add(new StoreDiffItem(CHANGED, row.shopCode(), row.storeName(), beforeChanged, afterChanged));
            }
        }

        int newHist = 0;
        for (Map.Entry<String, Integer> e : fileHistoryCounts.entrySet()) {
            newHist += Math.max(0, e.getValue() - existingHistoryCounts.getOrDefault(e.getKey(), 0));
        }
        int removedHist = 0;
        for (Map.Entry<String, Integer> e : existingHistoryCounts.entrySet()) {
            removedHist += Math.max(0, e.getValue() - fileHistoryCounts.getOrDefault(e.getKey(), 0));
        }
        return new Diff(items, new DiffSummary(newStores, changed, unchanged, 0, newHist, removedHist));
    }

    // ------------------------------------------------------------------ 값 모으기

    static Map<String, String> fileValues(StoreCsvRow r) {
        StoreAddressParser.Region region = StoreAddressParser.parse(r.region(), r.roadAddress(), r.jibunAddress());
        Map<String, String> m = new LinkedHashMap<>();
        m.put("판매점명", r.storeName());
        m.put("도로명주소", blankToNull(r.roadAddress()));
        m.put("지번주소", blankToNull(r.jibunAddress()));
        m.put("시도", region.sido());
        m.put("시군구", region.sigungu());
        m.put("위도", r.latitude() == null ? null : r.latitude().toPlainString());
        m.put("경도", r.longitude() == null ? null : r.longitude().toPlainString());
        m.put("폐점여부", String.valueOf(r.closed()));
        return m;
    }

    static Map<String, String> existingValues(ExistingStore s) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("판매점명", s.name());
        m.put("도로명주소", blankToNull(s.roadAddress()));
        m.put("지번주소", blankToNull(s.jibunAddress()));
        m.put("시도", s.sido());
        m.put("시군구", s.sigungu());
        m.put("위도", s.latitude() == null ? null : s.latitude().toPlainString());
        m.put("경도", s.longitude() == null ? null : s.longitude().toPlainString());
        m.put("폐점여부", String.valueOf(s.closed()));
        return m;
    }

    private static boolean sameValue(String key, String a, String b) {
        if ("위도".equals(key) || "경도".equals(key)) {
            if (a == null || b == null) {
                return a == null && b == null;
            }
            return new BigDecimal(a).compareTo(new BigDecimal(b)) == 0;
        }
        return Objects.equals(a, b);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
