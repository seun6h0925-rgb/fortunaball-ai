package com.fortunaball.content.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * 안내 콘텐츠 종류 (테이블 content_page 의 page_type 값과 1:1).
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 주소의 {contentType} 은 이 enum 이름과 글자가 똑같아야 한다 (예: /api/contents/CLAIM_GUIDE).
 *  - 메뉴 9 "당첨금 수령 가이드" 는 CLAIM_GUIDE 하나, 메뉴 10 "서비스 안내·유의사항" 은 나머지 8개다.
 *  - 종류를 늘릴 때는 이 enum 과 DB 의 page_type 설명(docs/db/schema.sql)을 같이 고친다.
 */
public enum ContentType {
    SERVICE_INTRO,      // 서비스 소개
    USAGE_GUIDE,        // 이용 방법 (무료 횟수 포함)
    PRIVACY,            // 개인정보 안내 (게스트 식별값 포함)
    AI_NOTICE,          // AI 이용 유의사항
    NO_GUARANTEE,       // 당첨 보장 불가 안내 (고정 문구 전체)
    GAMBLING_WARNING,   // 과몰입 주의·상담전화·19세 미만 구매 불가
    DISCLAIMER,         // 면책 고지
    CLAIM_GUIDE,        // 당첨금 수령 가이드 (메뉴 9)
    FAQ;                // 자주 묻는 질문

    /** 주소에서 받은 글자를 종류로 바꾼다. 없는 값이면 비어 있는 Optional (호출한 쪽이 400 으로 처리). */
    public static Optional<ContentType> from(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(t -> t.name().equals(code)).findFirst();
    }
}
