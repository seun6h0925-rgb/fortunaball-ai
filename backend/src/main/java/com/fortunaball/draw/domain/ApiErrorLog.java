package com.fortunaball.draw.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 외부 API 호출 오류 기록 (DB 테이블 api_error_log).
 * 공식 사이트(동행복권)에서 데이터를 가져오다 실패한 내용을 한 줄씩 남긴다.
 * 관리자 화면(오류 현황, 오류 유형 필터)과 오류일지 작성에 쓰인다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 오류 파일 로그(error.log)와 다르다. 이쪽은 DB에 쌓여서 관리자 화면에서 조회·필터할 수 있다.
 *  - created_at(발생일자)은 DB가 자동으로 채운다. 코드에서 넣지 않고, 읽기만 한다.
 *  - requestUrl, httpStatus 는 없는 오류(통신 자체가 안 된 경우)도 있어서 NULL 이 될 수 있다.
 *  - requestUrl 에는 키·발급코드 같은 민감한 값이나 이용자 위치를 넣지 않는다.
 *
 * 📚 배운 내용: @Entity, @Table, @Id, @GeneratedValue (JPA)
 */
@Entity
@Table(name = "api_error_log")
public class ApiErrorLog {

    /** 오류 유형 (관리자 화면 필터용). 새 유형이 필요하면 여기에 추가한다. */
    public static final String TYPE_NETWORK = "NETWORK";        // 연결 실패·시간 초과
    public static final String TYPE_HTTP_ERROR = "HTTP_ERROR";  // 응답은 왔지만 상태 코드가 정상이 아님
    public static final String TYPE_FORMAT = "FORMAT";          // 응답 형식이 달라 읽을 수 없음
    public static final String TYPE_UNKNOWN = "UNKNOWN";        // 위에 해당하지 않는 오류

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String apiName;
    private String errorType;
    private String requestUrl;
    private Integer httpStatus;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /** DB 가 자동으로 채우는 값이라 저장·수정 때는 보내지 않는다. */
    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ApiErrorLog() { }   // JPA 가 객체를 만들 때 쓰는 기본 생성자

    public ApiErrorLog(String apiName, String errorType, String requestUrl,
                       Integer httpStatus, String errorMessage) {
        this.apiName = apiName;
        this.errorType = errorType;
        this.requestUrl = requestUrl;
        this.httpStatus = httpStatus;
        this.errorMessage = errorMessage;
    }

    public Long getId() { return id; }
    public String getApiName() { return apiName; }
    public String getErrorType() { return errorType; }
    public String getRequestUrl() { return requestUrl; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
