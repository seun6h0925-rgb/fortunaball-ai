package com.fortunaball.dream.dto;

/**
 * AI 꿈풀이 요청 (API-08)
 *  dream               꿈 내용 (5자 이상 300자 이하). 서버에 저장하지 않고 AI 에도 보내지 않는다.
 *  supplementWithStats 번호가 6개보다 모자랄 때 통계로 채워도 되는지(true 면 보완). 생략하면 false.
 */
public record DreamRequest(String dream, Boolean supplementWithStats) {
}
