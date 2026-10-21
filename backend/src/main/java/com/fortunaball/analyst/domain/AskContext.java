package com.fortunaball.analyst.domain;

/**
 * 한 번의 질문이 어떤 화면에서, 위치와 함께 왔는지. AI 애널리스트가 질문을 처리하는 동안만 들고 다닌다.
 *
 *  map       지도 화면에서 연 대화인지
 *  location  사용자가 위치 스위치를 켠 경우의 위치. 없으면 null
 *
 * ⚠ 위치(위도·경도)는 AI 에게 보내지 않는다. 서버가 "내 주변" 조회를 직접 할 때만 쓰고, 저장·로그에 남기지 않는다.
 *    그래서 Location 의 toString 은 값을 숨긴다. (실수로 로그에 찍혀도 좌표가 나오지 않게)
 */
public record AskContext(boolean map, Location location) {

    /** 일반 애널리스트 화면, 위치 없음 */
    public static final AskContext NONE = new AskContext(false, null);

    public boolean hasLocation() {
        return location != null;
    }

    public record Location(double lat, double lng) {

        @Override
        public String toString() {
            return "Location[hidden]";
        }
    }
}
