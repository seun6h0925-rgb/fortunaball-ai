package com.fortunaball.admin.store.dto;

import java.time.LocalDate;

/** 파일 검증 결과. 여기까지 나왔으면 형식·열·회차(draw_round 존재)가 모두 맞는다는 뜻이다. */
public record UploadValidation(int totalRows, int storeCount, int minRound, int maxRound, LocalDate baseDate) {
}
