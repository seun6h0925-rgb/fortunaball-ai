package com.fortunaball.content.service;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.content.domain.ContentType;
import com.fortunaball.content.dto.ContentResponse;
import com.fortunaball.content.repository.ContentPageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 안내 콘텐츠 조회 (메뉴 9 수령 가이드, 메뉴 10 서비스 안내).
 *
 * ⚠ 오류 구분 (추적하기 쉽게 두 가지를 나눴다)
 *  - 정해진 종류(ContentType)에 없는 값  → 400 COMMON-001  (주소를 잘못 쓴 경우)
 *  - 종류는 맞는데 DB 에 없거나 숨김     → 404 COMMON-002  (글이 아직 준비되지 않은 경우: 시드 SQL 을 실행했는지 확인)
 */
@Service
public class ContentQueryService {

    private final ContentPageRepository repository;

    public ContentQueryService(ContentPageRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ContentResponse getByType(String contentType) {
        ContentType type = ContentType.from(contentType).orElseThrow(() -> new BusinessException(
                ErrorCode.INVALID_INPUT, "알 수 없는 안내 종류예요.", "contentType=" + contentType));

        return repository.findByPageTypeAndVisibleTrue(type.name())
                .map(ContentResponse::from)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND, "안내 내용을 준비 중이에요.", "content_page 없음 또는 숨김: " + type.name()));
    }
}
