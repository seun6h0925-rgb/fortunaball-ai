package com.fortunaball.admin.log.dto;

import java.time.LocalDateTime;

/** 외부 API 오류 로그 한 줄 (API-38). requestUrl, httpStatus 는 없을 수 있다. */
public record ErrorLogItem(Long id, String apiName, String errorType, String requestUrl, Integer httpStatus,
                           String errorMessage, LocalDateTime createdAt) {
}
