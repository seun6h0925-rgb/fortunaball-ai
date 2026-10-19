package com.fortunaball.issue.domain;

/**
 * 번호 생성 방식 (number_issue.issue_type)
 *  - STAT_AUTO   : 통계 번호 생성에서 패턴 없이 만든 번호
 *  - STAT_CUSTOM : 통계 번호 생성에서 패턴을 골라 만든 번호 (패턴 코드는 generation_basis_value 에 저장)
 *  - DREAM       : 꿈 번호 (메뉴 별도)
 *  - ANALYST     : 애널리스트 번호 (메뉴 별도)
 */
public enum IssueType {
    STAT_AUTO, STAT_CUSTOM, DREAM, ANALYST
}
