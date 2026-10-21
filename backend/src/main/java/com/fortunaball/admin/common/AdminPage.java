package com.fortunaball.admin.common;

import java.util.List;

/**
 * 관리자 목록 응답의 공통 모양. page 는 0부터 시작한다. 화면의 "더 보기"는 hasNext 로 판단한다.
 */
public record AdminPage<T>(List<T> items, int page, int size, long totalCount, boolean hasNext) {
}
