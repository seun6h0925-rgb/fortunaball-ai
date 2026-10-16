package com.fortunaball.draw.dto;

/** 한 회차를 수집한 결과 (DrawCollectService.collectRound 가 돌려준다) */
public enum CollectOutcome {
    /** 새로 가져와서 DB 에 저장함 */
    SAVED,
    /** 이미 DB 에 있어서 공식 사이트에 요청하지 않고 건너뜀 */
    SKIPPED_EXISTS,
    /** 공식 사이트에 아직 데이터가 없음 (마지막 회차를 지난 번호) */
    NO_DATA,
    /** 추첨 직후라 2~5등 값이 아직 0 → 저장하지 않음 (다음에 다시 받는다) */
    NOT_AGGREGATED
}
