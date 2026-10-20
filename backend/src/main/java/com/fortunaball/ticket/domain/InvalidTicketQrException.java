package com.fortunaball.ticket.domain;

/**
 * QR 글자가 로또 용지 형식이 아닐 때 던지는 예외.
 * 이 클래스는 순수 계산 쪽(domain)이라 스프링·HTTP 를 모른다. 서비스에서 400 오류와 화면 안내 문구로 바꿔준다.
 *
 * ⚠ 메시지에는 어느 부분이 틀렸는지만 적고, QR 글자 원문은 넣지 않는다. (TR 번호·일련번호가 로그에 남지 않게)
 */
public class InvalidTicketQrException extends RuntimeException {

    public InvalidTicketQrException(String message) {
        super(message);
    }
}
