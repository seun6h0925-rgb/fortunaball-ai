package com.fortunaball.dream.dto;

import com.fortunaball.analyst.domain.ChatTurn;
import java.util.List;

/**
 * AI 꿈풀이 요청 (API-08) - 대화로 이어지는 요청
 *
 * 처음 꿈을 들려줄 때는 dream 만 보낸다.
 * 그 뒤 사용자가 말로 답하면(예: "확정할게요", "네 채워 주세요", "3게임으로 나눠 주세요") 아래처럼 보낸다.
 *
 *  dream         처음에 들려준 꿈 내용 (5자 이상 300자 이하). 서버는 꿈 원문을 저장하지 않으므로 이어지는 요청에도 화면이 다시 보낸다.
 *                서버에 저장하지 않고 AI 에도 보내지 않는다.
 *  reply         이번에 사용자가 입력한 말. 비어 있으면 처음 풀이이고, 있으면 직전 질문에 대한 답으로 읽는다.
 *                질문에 대한 답이 아니면(새 꿈 이야기, 인사 등) 새 이야기로 보고 처음부터 다시 처리한다.
 *  step          직전 응답의 step 값을 그대로 돌려준다. (서버가 지금 어떤 질문 중이었는지 알기 위해서다)
 *  keep          직전 응답의 keep 값을 그대로 돌려준다. (지금 쓰고 있는 꿈에서 나온 번호들. 뺄 번호를 뺀 뒤의 번호도 이 값으로 이어진다)
 *  gameCount     직전 응답의 gameCount 값을 그대로 돌려준다. (여러 게임으로 나눴을 때 다시 만들기에 쓴다. 비어 있으면 1게임)
 *  confirmToken  직전 응답의 confirmToken. "확정할게요"라고 답했을 때 확정 처리에 쓴다.
 *  ongoing       지금 이야기 중인 꿈(화면이 들고 있는 처음 꿈). 새 이야기처럼 보내는 말(reply 없음)에도 같이 보낸다.
 *                꿈과 상관없는 질문("이 꿈에 맞는 번호가 있어?")이면 서버가 이 꿈을 기억하는 것처럼 이어서 답한다. 없으면 null
 *  history       꿈에 대해 주고받은 최근 대화 (오래된 것부터, 최근 6개까지만 쓴다). 서버는 저장하지 않고 화면이 요청마다 보낸다. 없으면 null
 *
 * 버튼은 이 말들을 대신 보내 주는 단축 칩일 뿐이다. (응답의 quickReplies 문구를 reply 로 그대로 보낸다)
 */
public record DreamRequest(String dream, String reply, String step, List<Integer> keep, Integer gameCount,
                           String confirmToken, String ongoing, List<ChatTurn> history) {

    /** 이어지는 대화 값(ongoing, history) 없이 만드는 요청. 기존 호출과 테스트가 그대로 쓴다. */
    public DreamRequest(String dream, String reply, String step, List<Integer> keep, Integer gameCount,
                        String confirmToken) {
        this(dream, reply, step, keep, gameCount, confirmToken, null, null);
    }

    /** 처음 꿈을 들려주는 요청 */
    public static DreamRequest first(String dream) {
        return new DreamRequest(dream, null, null, null, null, null);
    }
}
