package com.fortunaball.notice.service;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.notice.dto.NoticeDetailResponse;
import com.fortunaball.notice.dto.NoticePageResponse;
import com.fortunaball.notice.dto.NoticeSummaryResponse;
import com.fortunaball.notice.repository.NoticeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 손님용 공지 조회 (API-18, 19). 공개된 공지만 보여 준다. */
@Service
public class NoticeService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;

    private final NoticeRepository repository;

    public NoticeService(NoticeRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public NoticePageResponse<NoticeSummaryResponse> list(Integer page, Integer size) {
        return NoticePageResponse.from(repository
                .findByVisibleTrueOrderByCreatedAtDescIdDesc(PageRequest.of(safePage(page), safeSize(size)))
                .map(NoticeSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public NoticeDetailResponse detail(Long noticeId) {
        return repository.findByIdAndVisibleTrue(noticeId)
                .map(NoticeDetailResponse::from)
                // 비공개 공지도 "없다"고만 답한다. 비공개 글이 있다는 사실도 알려 주지 않는다.
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "공지사항을 찾을 수 없어요.", "공지 없음 또는 비공개: " + noticeId));
    }

    /** 목록 파라미터 보정 (page 는 0 이상, size 는 1~50, 안 보내면 20) */
    static int safePage(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    static int safeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
