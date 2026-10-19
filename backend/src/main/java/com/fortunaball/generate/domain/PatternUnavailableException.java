package com.fortunaball.generate.domain;

/**
 * 선택한 패턴의 조건을 만족하는 번호를 만들 수 없을 때 던진다.
 * 예) 겨울잠 번호 패턴인데 최근 20회에 1~45 가 모두 나와서 "겨울잠 번호"가 하나도 없는 경우.
 * 패턴 조건은 완화하지 않기로 했으므로(명세), 억지로 다른 번호를 만들지 않고 이 예외로 알린다.
 * 어떻게 응답할지는 API 단계(서비스)에서 정한다.
 */
public class PatternUnavailableException extends RuntimeException {

    public PatternUnavailableException(String message) {
        super(message);
    }
}
