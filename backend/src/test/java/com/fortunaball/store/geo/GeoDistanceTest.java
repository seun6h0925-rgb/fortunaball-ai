package com.fortunaball.store.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 거리 계산과 네모 범위 확인. */
class GeoDistanceTest {

    @Test
    void 같은_위치는_거리_0() {
        assertEquals(0.0, GeoDistance.meters(37.5663, 126.9779, 37.5663, 126.9779), 0.001);
    }

    @Test
    void 시청에서_광화문까지는_1km_남짓() {
        double d = GeoDistance.meters(37.5663, 126.9779, 37.5759, 126.9769);

        assertTrue(d > 1000 && d < 1150, "거리: " + d);
    }

    @Test
    void 위도_0_01도는_약_1_1km() {
        double d = GeoDistance.meters(37.0, 127.0, 37.01, 127.0);

        assertEquals(1112, d, 5);
    }

    @Test
    void 네모_범위는_반경_안의_점을_모두_담는다() {
        double lat = 37.5663;
        double lng = 126.9779;
        GeoDistance.Box box = GeoDistance.boundingBox(lat, lng, 1000);

        // 정북·정동으로 약 1km 떨어진 점 (경도 1도 길이는 위도에 따라 달라서 계산식으로 만든다)
        double northLat = lat + 1000 / 111_320.0;
        double eastLng = lng + 1000 / (111_320.0 * Math.cos(Math.toRadians(lat)));

        assertTrue(box.minLat() <= lat && lat <= box.maxLat());
        assertTrue(box.maxLat() >= northLat - 1e-9);
        assertTrue(box.maxLng() >= eastLng - 1e-9);
        assertTrue(box.minLng() < lng && box.minLat() < lat);
    }
}
