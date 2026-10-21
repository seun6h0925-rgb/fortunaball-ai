package com.fortunaball.store.geo;

/**
 * 두 위치 사이의 거리 계산 (내 주변 판매점 조회용).
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 거리는 지구를 공으로 보고 계산하는 "하버사인" 공식이다. 반경 1km 안에서는 오차가 아주 작다.
 *  - DB 에서 한 번에 거르기 위해 먼저 "네모난 범위(boundingBox)"로 후보를 줄이고, 정확한 거리는 여기서 계산해 걸러 낸다.
 *    (DB 의 좌표에는 색인 idx_lotto_store_geo 가 있어서 네모 범위 검색은 빠르다)
 *  - 사용자의 위치값은 이 클래스에서도 저장하거나 기록하지 않는다. 계산만 하고 버린다.
 */
public final class GeoDistance {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;

    private GeoDistance() { }

    /** 두 지점의 거리 (미터) */
    public static double meters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /** 중심에서 반경(미터) 안을 모두 담는 네모 범위 */
    public record Box(double minLat, double maxLat, double minLng, double maxLng) { }

    public static Box boundingBox(double lat, double lng, double radiusMeters) {
        double dLat = radiusMeters / METERS_PER_DEGREE_LAT;
        // 경도 1도의 길이는 위도가 높을수록 짧아진다. 극지방 근처의 0 나누기를 막기 위해 아주 작은 값은 막는다.
        double cos = Math.max(Math.cos(Math.toRadians(lat)), 0.01);
        double dLng = radiusMeters / (METERS_PER_DEGREE_LAT * cos);
        return new Box(lat - dLat, lat + dLat, lng - dLng, lng + dLng);
    }
}
