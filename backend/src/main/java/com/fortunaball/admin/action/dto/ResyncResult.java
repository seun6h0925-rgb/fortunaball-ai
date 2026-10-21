package com.fortunaball.admin.action.dto;

import java.util.List;

/**
 * 회차 재수집 결과 (API-23).
 * result: CREATED(없던 회차를 새로 저장) / UPDATED(값이 달라서 덮어씀) / UNCHANGED(공식 값과 같아서 그대로 둠)
 */
public record ResyncResult(
        int roundNo,
        String result,
        String changes,
        boolean prizeCheckPassed,
        String prizeCheckMessage,
        List<PrizeLine> prizes) {

    public record PrizeLine(int rank, long prizeAmount, int winnerCount) {
    }
}
