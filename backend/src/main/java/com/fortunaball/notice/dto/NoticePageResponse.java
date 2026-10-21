package com.fortunaball.notice.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** 공지 목록 묶음. 화면의 "더 보기"는 hasNext 로 판단한다. page 는 0부터 시작한다. */
public record NoticePageResponse<T>(List<T> items, int page, int size, long totalCount, boolean hasNext) {

    public static <T> NoticePageResponse<T> from(Page<T> p) {
        return new NoticePageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.hasNext());
    }
}
