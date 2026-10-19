package com.fortunaball.generate.service;

/**
 * 번호 생성 알고리즘의 기준값 모음. 숫자를 바꾸고 싶으면 이 파일만 고치면 된다.
 * (패턴 자체의 규칙은 stat 쪽 PatternRule 에 있다)
 */
public final class GenerationRule {

    private GenerationRule() {
    }

    /** 게임 하나를 뽑을 때 만드는 후보 수 */
    public static final int CANDIDATES_PER_GAME = 300;
    /** 점수 상위 몇 개 중에서 랜덤으로 뽑을지 (다양성을 위해 1등만 고르지 않는다) */
    public static final int TOP_POOL_SIZE = 10;
    /** 이 개수보다 후보가 적으면 검증 기준을 한 단계 완화한다 */
    public static final int MIN_VALID_CANDIDATES = 20;
    /** 후보를 못 찾았을 때 후보를 다시 만드는 최대 횟수 */
    public static final int MAX_SAMPLING_ROUNDS = 5;
    /** 패턴 조건을 만족하는 후보를 무작위로 찾을 때의 최대 시도 횟수 */
    public static final int MAX_PATTERN_ATTEMPTS = 2000;

    /** 공통 검증 범위: 최근 100회 분포의 몇 %~몇 % 안이면 통과 (실제 당첨번호의 약 90%가 통과하는 넓은 범위) */
    public static final int BAND_LOW_PERCENT = 5;
    public static final int BAND_HIGH_PERCENT = 95;
    /** 점수 가산용 중심 범위 (분포의 25%~75%) */
    public static final int CORE_LOW_PERCENT = 25;
    public static final int CORE_HIGH_PERCENT = 75;

    /** 쏠림 패턴 감점 */
    public static final int BIRTHDAY_MAX_NUMBER = 31;   // 생일 번호(1~31)만 모인 조합
    public static final int PENALTY_BIRTHDAY = 30;
    public static final int PENALTY_RUN_OF_THREE = 25;  // 연속 번호가 3개 이상 이어진 조합
    public static final int PENALTY_ARITHMETIC = 40;    // 같은 간격으로 4개 이상 이어진 조합(등차수열)

    /** 기본 점수와, 중심 범위에 들어온 지표 하나당 가산점 */
    public static final int BASE_SCORE = 100;
    public static final int BONUS_PER_CORE_METRIC = 5;
}
