package com.fortunaball.ticket.dto;

/**
 * 용지 당첨 확인 요청 (API-45)
 * 값이 빠져도 우리 오류 형식(400)으로 돌려주려고 서비스에서 직접 검사한다.
 *
 * @param qrText 브라우저가 용지 QR 에서 읽은 글자 전체 (예: http://qr.dhlottery.co.kr/?v=1239m0306...)
 */
public record TicketCheckRequest(String qrText) {
}
