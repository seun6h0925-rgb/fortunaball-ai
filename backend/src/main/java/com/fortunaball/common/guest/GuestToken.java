package com.fortunaball.common.guest;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;

import java.util.regex.Pattern;

/**
 * 비회원 구분값(X-Guest-Token). 화면(브라우저)이 UUID 로 만들어 보내고, 서버는 "형식"만 검사한다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 토큰은 사용자가 마음대로 바꿀 수 있는 값이다. 그래서 "같은 조합 3회 제한"은 토큰이 아니라 서비스 전체 기준이다.
 *  - 형식: 영문·숫자·'-' 만, 16~128자. (DB number_issue.guest_token 이 VARCHAR(128))
 *  - 토큰 값은 개인 식별에 가까우므로 로그·오류 상세에 값 자체를 남기지 않는다. (길이만 남김)
 *  - toString() 도 마스킹해서, 실수로 로그에 찍혀도 값이 나가지 않게 한다.
 */
public record GuestToken(String value) {

    public static final String HEADER_NAME = "X-Guest-Token";

    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9-]{16,128}$");

    /** 헤더 값으로 토큰을 만든다. 없거나 형식이 틀리면 BusinessException(COMMON-001) */
    public static GuestToken of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "비회원 확인 정보가 없어요. 화면을 새로고침한 뒤 다시 시도해 주세요.",
                    HEADER_NAME + " 헤더 없음");
        }
        String trimmed = raw.trim();
        if (!FORMAT.matcher(trimmed).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "비회원 확인 정보가 올바르지 않아요. 화면을 새로고침한 뒤 다시 시도해 주세요.",
                    HEADER_NAME + " 형식 오류 length=" + trimmed.length());
        }
        return new GuestToken(trimmed);
    }

    /** 로그용 마스킹: 앞 4자리만 보이고 나머지는 가린다 */
    @Override
    public String toString() {
        return "GuestToken[" + value.substring(0, 4) + "****]";
    }
}
