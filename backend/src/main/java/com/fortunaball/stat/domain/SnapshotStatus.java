package com.fortunaball.stat.domain;

/**
 * 회차별 통계의 상태. DB(stat_round_snapshot.status)에는 name() 값이 그대로 저장된다.
 *  PREPARING : 사전 분석 - 직전 확정 회차까지의 결과로 미리 만들어 둔 통계 (아직 대상 회차 추첨 전)
 *  WAITING   : 결과 대기 - 대상 회차 추첨 시각(토 20:00)이 지났고 공식 결과가 아직 안 올라온 상태
 *  CONFIRMED : 확정 - 대상 회차 결과까지 반영해 확정한 통계
 * 화면에는 label() 문구로 보여 준다. (사용자에게는 "사전 분석 / 확정"이 주로 보인다)
 */
public enum SnapshotStatus {

    PREPARING("사전 분석"),
    WAITING("결과 대기"),
    CONFIRMED("확정");

    private final String label;

    SnapshotStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
