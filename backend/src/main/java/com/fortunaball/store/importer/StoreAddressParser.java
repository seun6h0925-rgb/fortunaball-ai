package com.fortunaball.store.importer;

/**
 * 판매점 주소에서 시도·시군구를 뽑는다 (지역별 조회용).
 *
 * 규칙
 *  - 시도: CSV 의 region 칸을 그대로 쓴다 (예: 서울, 경기, 전남광주). region 이 비어 있으면 주소의 첫 단어를 쓴다.
 *  - 시군구: 주소의 두 번째 단어가 "시/군/구"로 끝날 때만 쓴다. (예: "서울 중구 퇴계로56길 57" → 중구,
 *    "경기 수원시 영통구 ..." → 수원시)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 세종은 주소가 "세종 조치원로 9-1" 처럼 시군구가 없다. 이때 시군구는 null (세종 전체가 하나의 지역).
 *  - 도로명 주소가 없는 판매점(폐점 대부분)은 지번 주소로 대신 뽑는다.
 *  - region 의 "전남광주" 는 자료에 있는 이름을 그대로 쓴 것이다 (임의로 광주/전남으로 나누지 않는다).
 */
public final class StoreAddressParser {

    public record Region(String sido, String sigungu) { }

    private StoreAddressParser() { }

    public static Region parse(String region, String roadAddress, String jibunAddress) {
        String base = isBlank(roadAddress) ? jibunAddress : roadAddress;
        String[] tokens = isBlank(base) ? new String[0] : base.trim().split("\\s+");

        String sido = !isBlank(region) ? region.trim() : (tokens.length > 0 ? tokens[0] : null);
        String sigungu = null;
        if (tokens.length > 1 && endsWithSiGunGu(tokens[1])) {
            sigungu = tokens[1];
        }
        return new Region(sido, sigungu);
    }

    private static boolean endsWithSiGunGu(String word) {
        char last = word.charAt(word.length() - 1);
        return last == '시' || last == '군' || last == '구';
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
