package com.fortunaball.analyst.domain;

/**
 * 이전 대화 한 줄. 대화는 화면(프론트)이 들고 있다가 요청마다 최근 몇 턴을 같이 보낸다. 서버는 저장하지 않는다.
 *
 *  role     "user"(사용자) 또는 "assistant"(AI 답변)
 *  content  내용. assistant 는 응답의 historyText 를 그대로 보내면 된다.
 */
public record ChatTurn(String role, String content) {

    public static final String USER = "user";
    public static final String ASSISTANT = "assistant";

    public boolean isUser() {
        return USER.equals(role);
    }
}
