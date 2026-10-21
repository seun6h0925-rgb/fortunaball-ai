package com.fortunaball.dream.domain;

/**
 * 추천 번호가 어디서 왔는지. 화면에 출처를 구분해서 보여주기 위한 값이다.
 *
 *  DREAM_TEXT  사용자가 꿈 내용에 직접 적은 숫자 (한국 전통: 꿈에 나온 숫자를 그대로 쓴다)
 *  SMORFIA     나폴리 스모르피아 전통 번호 (1~45만 사용)
 *  CHARADA     쿠바 차라다 전통 번호 (1~45만 사용)
 *  SERVICE     서비스 내부 참고 매핑
 *  STAT        꿈 번호가 6개보다 모자라서 번호 생성 기능이 통계로 채운 번호 (다시 생성하면 이 번호만 바뀐다)
 *
 * 앞의 SMORFIA / CHARADA / SERVICE 는 DB dream_symbol_number.number_origin 값과 같다.
 */
public enum DreamNumberOrigin {
    DREAM_TEXT,
    SMORFIA,
    CHARADA,
    SERVICE,
    STAT;

    /** DB 값을 읽을 때 모르는 값이 들어와도 서비스 매핑으로 취급한다. */
    public static DreamNumberOrigin fromDb(String value) {
        if (value == null) {
            return SERVICE;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return SERVICE;
        }
    }
}
