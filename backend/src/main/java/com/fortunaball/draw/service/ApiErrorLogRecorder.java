package com.fortunaball.draw.service;

import com.fortunaball.draw.domain.ApiErrorLog;
import com.fortunaball.draw.repository.ApiErrorLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 API 호출 오류를 DB(api_error_log)에 남기는 담당.
 *
 * 사용 예
 *   recorder.record("DHLOTTERY_DRAW", ApiErrorLog.TYPE_HTTP_ERROR, url, 503, "점검 중");
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 오류를 기록하다 또 오류가 나도 "원래 작업"은 멈추지 않는다. (파일 로그 app.log 에만 남기고 넘어감)
 *    DB 가 문제라서 오류를 못 남기는 상황에서도 서버가 죽지 않게 하기 위해서다.
 *  - 이 클래스는 다른 클래스에서 불러야 @Transactional 이 적용된다.
 *  - 너무 긴 값은 잘라서 저장한다. (request_url 은 500자, 메시지는 2000자까지)
 */
@Component
public class ApiErrorLogRecorder {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorLogRecorder.class);
    private static final int MAX_URL_LENGTH = 500;       // DB 컬럼 크기(VARCHAR 500)에 맞춤
    private static final int MAX_API_NAME_LENGTH = 50;   // DB 컬럼 크기(VARCHAR 50)에 맞춤
    private static final int MAX_TYPE_LENGTH = 30;       // DB 컬럼 크기(VARCHAR 30)에 맞춤
    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final ApiErrorLogRepository repository;

    public ApiErrorLogRecorder(ApiErrorLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String apiName, String errorType, String requestUrl,
                       Integer httpStatus, String errorMessage) {
        try {
            repository.save(new ApiErrorLog(
                    cut(apiName, MAX_API_NAME_LENGTH),
                    cut(errorType, MAX_TYPE_LENGTH),
                    cut(requestUrl, MAX_URL_LENGTH),
                    httpStatus,
                    errorMessage == null ? "(메시지 없음)" : cut(errorMessage, MAX_MESSAGE_LENGTH)));
        } catch (RuntimeException e) {
            log.warn("[오류기록] DB 저장 실패 (작업은 계속함): api={} type={} / {}", apiName, errorType, e.toString());
        }
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
