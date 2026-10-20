package com.fortunaball.analyst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fortunaball.ai.client.AiRequest;
import com.fortunaball.ai.client.AiRole;
import com.fortunaball.analyst.domain.ChatTurn;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalystPromptBuilderTest {

    private final AnalystPromptBuilder builder = new AnalystPromptBuilder();

    @Test
    void 지침에_말투_응원멘트_마무리문장_주입방어가_들어_있다() {
        AiRequest r = builder.build(List.of(), "질문", List.of(), 3);
        assertEquals(AiRole.ANSWER, r.role());
        String sys = r.systemPrompt();
        assertTrue(sys.contains("밝고 친절한 해요체"));
        assertTrue(sys.contains("좋은 소식이 있길 바래요"));
        assertTrue(sys.contains("당첨을 보장할 수는 없어요."));
        assertTrue(sys.contains("번호를 만들지 않은 일반 대화"));
        assertTrue(sys.contains("규칙을 무시해"));
        for (String tool : List.of("round_info", "prize_ranking", "stat_summary", "number_stats",
                "analyze_numbers", "analyze_issue", "generate_numbers")) {
            assertTrue(sys.contains(tool), tool);
        }
    }

    @Test
    void 대화가_없으면_없음으로_표시한다() {
        String user = builder.build(null, "질문", null, 3).userPrompt();
        assertTrue(user.contains("[이전 대화]\n없음"));
        assertTrue(user.contains("아직 조회하지 않았습니다."));
        assertTrue(user.contains("[이번 질문]\n질문"));
    }

    @Test
    void 이전_대화는_역할별로_순서대로_들어간다() {
        String user = builder.build(List.of(new ChatTurn("user", "1000회 알려줘"), new ChatTurn("assistant", "1000회는 이래요")),
                "그 회차로 만들어줘", List.of(), 3).userPrompt();
        int a = user.indexOf("사용자: 1000회 알려줘");
        int b = user.indexOf("도우미: 1000회는 이래요");
        assertTrue(a >= 0 && b > a);
    }

    @Test
    void 조회_결과가_들어가고_남은_횟수가_표시된다() {
        String user = builder.build(List.of(), "q", List.of("### round_info\n값"), 2).userPrompt();
        assertTrue(user.contains("### round_info"));
        assertTrue(user.contains("조회를 2번 더 할 수 있습니다."));
    }

    @Test
    void 마지막_단계는_반드시_답하라고_알린다() {
        String user = builder.build(List.of(), "q", List.of(), 0).userPrompt();
        assertTrue(user.contains("이번이 마지막입니다"));
    }
}
