package com.fortunaball.admin.common;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 관리자 조회 API 가 함께 쓰는 입력 보정 도구. (쪽 번호·크기, 기간)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 기간은 "날짜"로 받고(from, to 모두 그 날 포함), 내부에서는 [시작 0시, 마지막 날 다음날 0시) 로 바꿔 쓴다.
 *  - 날짜 기준은 서울 시간이다. (AI 한도 계산과 같은 기준)
 *  - 기간을 안 보내면 오늘부터 defaultDays 일 전까지 본다.
 */
public final class AdminQuery {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private AdminQuery() {
    }

    /** 시작(포함) ~ 끝(미포함) */
    public record Range(LocalDateTime start, LocalDateTime endExclusive) {
    }

    public static int page(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    public static int size(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static Range range(LocalDate from, LocalDate to, int defaultDays) {
        LocalDate end = to != null ? to : today();
        LocalDate start = from != null ? from : end.minusDays(defaultDays - 1L);
        if (start.isAfter(end)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "시작 날짜가 끝 날짜보다 늦어요.",
                    "기간 오류: from=" + start + ", to=" + end);
        }
        return new Range(start.atStartOfDay(), end.plusDays(1).atStartOfDay());
    }
}
