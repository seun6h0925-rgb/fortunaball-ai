package com.fortunaball.dream.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fortunaball.dream.dto.DreamResponse;
import org.junit.jupiter.api.Test;

/** 꿈풀이 대화 글 검사: 숫자·단정 표현·무관 표시·길이를 어기면 쓰지 않는다. */
class DreamChatTest {

    private final DreamChat chat = new DreamChat();

    @Test
    void 평범한_대화는_그대로_쓴다() {
        assertThat(chat.clean("  괜찮아요, 기억나는 장면부터 편하게 들려주세요.  "))
                .isEqualTo("괜찮아요, 기억나는 장면부터 편하게 들려주세요.");
    }

    @Test
    void 무관_표시면_쓰지_않는다() {
        assertThat(chat.clean("[OFF_TOPIC]")).isNull();
    }

    @Test
    void 숫자가_들어_있으면_쓰지_않는다() {
        assertThat(chat.clean("이번에는 7번과 12번이 좋아 보여요.")).isNull();
    }

    @Test
    void 당첨_보장_표현이면_쓰지_않는다() {
        assertThat(chat.clean("이 꿈이면 반드시 좋은 일이 있어요.")).isNull();
    }

    @Test
    void 비었거나_너무_길면_쓰지_않는다() {
        assertThat(chat.clean(null)).isNull();
        assertThat(chat.clean("   ")).isNull();
        assertThat(chat.clean("가".repeat(DreamChat.MAX_CHARS + 1))).isNull();
    }

    @Test
    void 요청에_직전_질문이_들어간다() {
        String input = chat.request("꿈이 기억 안 나요", DreamResponse.STEP_ASK_STAT_NUMBERS).userPrompt();
        assertThat(input).contains("통계").contains("꿈이 기억 안 나요");
    }
}
