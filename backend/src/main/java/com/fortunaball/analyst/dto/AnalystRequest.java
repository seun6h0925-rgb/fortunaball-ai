package com.fortunaball.analyst.dto;

import com.fortunaball.analyst.domain.ChatTurn;
import java.util.List;

/**
 * AI 로또 애널리스트 요청 (API-07)
 *
 *  question  사용자가 입력한 질문 (1~300자). 저장하지 않는다.
 *  context   지도 화면에서 온 질문이면 "MAP", 일반 애널리스트면 비워 둔다.
 *  lat, lng  지도 AI 에서 위치 스위치를 켠 경우에만 함께 보낸다. (둘 다 있어야 한다)
 *            위치 값은 저장·로그·AI 전달 없이 "위치가 있는지"만 판단하는 데 쓴다.
 *  history   이어지는 대화일 때 이전 대화 (오래된 것부터). 최근 6개까지만 쓴다. 첫 질문이면 비우거나 생략한다.
 *            화면이 들고 있다가 보내는 값이고, 서버는 저장하지 않는다.
 */
public record AnalystRequest(String question, String context, Double lat, Double lng, List<ChatTurn> history) {
}
