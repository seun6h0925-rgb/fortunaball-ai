package com.fortunaball.dream.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fortunaball.ai.AiSession;
import com.fortunaball.ai.client.AiRequest;
import com.fortunaball.ai.client.AiResponse;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.dream.service.DreamNarrator.Scene;
import java.util.List;
import org.junit.jupiter.api.Test;

class DreamNarratorTest {

    private final DreamNarrator narrator = new DreamNarrator();
    private static final String LINE = "[돼지 → 4·32]";

    private AiSession answering(String text) {
        AiSession session = mock(AiSession.class);
        when(session.call(any(AiRequest.class))).thenReturn(new AiResponse(text, "m", 1, 1));
        return session;
    }

    @Test
    void 두_줄짜리_상황은_첫마디와_질문을_돌려주고_상징_자리는_실제_내용으로_바뀐다() {
        List<String> lines = narrator.narrate(answering("와, 좋은 꿈이에요! {상징}을 찾았어요.\n이 번호로 확정하시겠어요?"), Scene.FULL, 0, LINE);

        assertEquals(List.of("와, 좋은 꿈이에요! " + LINE + "을 찾았어요.", "이 번호로 확정하시겠어요?"), lines);
    }

    @Test
    void 한_줄짜리_상황은_한_단락을_돌려준다() {
        List<String> lines = narrator.narrate(answering("3게임으로 나눠 담았어요. 이 번호로 확정하시겠어요?"), Scene.MULTI, 3, LINE);

        assertEquals(List.of("3게임으로 나눠 담았어요. 이 번호로 확정하시겠어요?"), lines);
    }

    @Test
    void AI_가_실패하면_null_이라서_정해진_문구를_쓴다() {
        AiSession session = mock(AiSession.class);
        when(session.call(any(AiRequest.class))).thenThrow(new BusinessException(ErrorCode.AI_CALL_FAILED, "실패", "x"));

        assertNull(narrator.narrate(session, Scene.FULL, 0, LINE));
        assertNull(narrator.narrate(answering(""), Scene.FULL, 0, LINE));
    }

    @Test
    void 줄_수가_다르거나_단정_표현이나_버튼_말이_있으면_쓰지_않는다() {
        // 두 줄이어야 하는데 한 줄
        assertNull(narrator.validated(Scene.FULL, "{상징}을 찾았어요. 확정하시겠어요?", LINE));
        // 한 줄이어야 하는데 두 줄
        assertNull(narrator.validated(Scene.MULTI, "첫 줄\n둘째 줄", LINE));
        // 단정 표현
        assertNull(narrator.validated(Scene.FULL, "{상징} 반드시 당첨돼요!\n확정하시겠어요?", LINE));
        // 사용자는 말로 답하므로 버튼·클릭 말은 쓰지 않는다
        assertNull(narrator.validated(Scene.FULL, "{상징}이에요.\n버튼을 눌러 확정하세요.", LINE));
        // 너무 길다
        assertNull(narrator.validated(Scene.MULTI, "가".repeat(400), LINE));
    }

    @Test
    void 상징_자리표시가_빠졌으면_쓰지_않는다() {
        // 번호와 상징 이름은 AI 가 쓰지 않고 서버가 넣는다. 자리표시가 없으면 필요한 내용이 빠진 것이다
        assertNull(narrator.validated(Scene.FULL, "좋은 꿈이에요!\n확정하시겠어요?", LINE));
        assertNull(narrator.validated(Scene.ASK_FILL, "상징을 찾았어요.\n채워도 될까요?", LINE));
    }

    @Test
    void 다시_만들면_1회_쓰인다는_안내가_빠졌으면_쓰지_않는다() {
        assertNull(narrator.validated(Scene.FILLED_AFTER_CONSENT, "통계로 채웠어요. 확정하시겠어요, 다시 만들까요?", LINE));
        assertEquals(List.of("통계로 채웠어요. 다시 만들면 무료 횟수가 1회 사용돼요."),
                narrator.validated(Scene.FILLED_AFTER_CONSENT, "통계로 채웠어요. 다시 만들면 무료 횟수가 1회 사용돼요.", LINE));
        // 두 줄짜리는 마지막 줄(질문)에 안내가 있어야 한다
        assertNull(narrator.validated(Scene.FILLED, "{상징}을 찾았어요. 1회\n확정하시겠어요?", LINE));
        assertTrue(narrator.validated(Scene.FILLED, "{상징}을 찾았어요.\n확정할까요, 다시 만들까요? 다시 만들면 1회 쓰여요.", LINE) != null);
    }
}
