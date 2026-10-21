package com.fortunaball.admin.store.dto;

/**
 * 판매점 파일과 현재 DB 의 변경점 요약. (API-30, 49)
 * 이미 반영·취소된 업로드는 "지금 DB 와 비교"가 의미 없어서 신규·변경 건수만 남기고 나머지는 null 이다.
 *
 * @param newStores        파일에는 있고 DB 에는 없는 판매점 (판매점 번호 기준)
 * @param changedStores    둘 다 있지만 이름·주소·좌표·폐점 여부가 다른 판매점
 * @param unchangedStores  똑같은 판매점
 * @param deletedStores    이 파일 종류(회차별 당첨 CSV)는 판매점을 삭제하지 않아 항상 0
 * @param newHistories     파일에만 있는 당첨 이력 건수 (회차·등수·구분 기준)
 * @param removedHistories 파일의 회차 범위 안에서 DB 에만 있는 당첨 이력 건수 (반영하면 지워진다)
 */
public record DiffSummary(Integer newStores, Integer changedStores, Integer unchangedStores, Integer deletedStores,
                          Integer newHistories, Integer removedHistories) {
}
