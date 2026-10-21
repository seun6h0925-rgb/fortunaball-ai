package com.fortunaball.dream.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortunaball.ai.client.AiRequest;
import com.fortunaball.ai.client.AiRole;
import com.fortunaball.dream.dto.DreamResponse;
import com.fortunaball.dream.service.DreamReplyReader.Intent;
import com.fortunaball.dream.service.DreamReplyReader.Reading;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DreamReplyReaderTest {

    private final DreamReplyReader reader = new DreamReplyReader(new ObjectMapper());
    private static final Set<Integer> NUMBERS = Set.of(3, 7, 9, 12, 22, 35);

    private Intent quick(String step, String reply) {
        return reader.quick(step, reply, NUMBERS).intent();
    }

    @Test
    void 확정을_묻는_중에는_확정과_다시_만들기를_알아본다() {
        assertEquals(Intent.CONFIRM, quick(DreamResponse.STEP_CONFIRM, "확정할게요"));
        assertEquals(Intent.CONFIRM, quick(DreamResponse.STEP_CONFIRM, "네"));
        assertEquals(Intent.CONFIRM, quick(DreamResponse.STEP_CONFIRM, "이대로 해주세요!"));
        assertEquals(Intent.REGENERATE, quick(DreamResponse.STEP_CONFIRM, "다시 만들어 주세요"));
        // 다시 만들기를 먼저 본다
        assertEquals(Intent.REGENERATE, quick(DreamResponse.STEP_CONFIRM, "다시 확정할게요"));
        assertEquals(Intent.UNCLEAR, quick(DreamResponse.STEP_CONFIRM, "음 글쎄"));
    }

    @Test
    void 긴_말은_단순한_대답으로_보지_않는다() {
        // 꿈 이야기 안에 "네", "다시" 같은 글자가 들어 있어도 대답으로 읽지 않는다
        assertEquals(Intent.UNCLEAR, quick(DreamResponse.STEP_CONFIRM, "어젯밤 꿈에서 다시 만난 친구가 네 번이나 나왔어요"));
        assertEquals(Intent.UNCLEAR, quick(DreamResponse.STEP_ASK_FILL, "돼지가 나오는 꿈이었는데 네 마리가 있었어요"));
    }

    @Test
    void 채우기와_통계_번호를_묻는_중에는_예_아니요를_알아본다() {
        assertEquals(Intent.YES, quick(DreamResponse.STEP_ASK_FILL, "네, 채워 주세요"));
        assertEquals(Intent.YES, quick(DreamResponse.STEP_ASK_STAT_NUMBERS, "네, 통계 번호 만들어 주세요"));
        assertEquals(Intent.NO, quick(DreamResponse.STEP_ASK_FILL, "아니요 다른 꿈 이야기할래요"));
        assertEquals(Intent.NO, quick(DreamResponse.STEP_ASK_FILL, "아니요 괜찮아요"));   // '괜찮'이 예로 읽히지 않는다
        assertEquals(Intent.UNCLEAR, quick(DreamResponse.STEP_ASK_FILL, "글쎄요"));
    }

    @Test
    void 몇_게임으로_나눌지_묻는_중에는_게임_수와_금액을_읽는다() {
        assertEquals(3, reader.quick(DreamResponse.STEP_ASK_OVERFLOW, "3게임으로 나눠 주세요", NUMBERS).gameCount());
        assertEquals(3, reader.quick(DreamResponse.STEP_ASK_OVERFLOW, "3게임만 할게, 3천원어치", NUMBERS).gameCount());
        assertEquals(5, reader.quick(DreamResponse.STEP_ASK_OVERFLOW, "5000원어치요", NUMBERS).gameCount());
        assertEquals(10, reader.quick(DreamResponse.STEP_ASK_OVERFLOW, "1만원이요", NUMBERS).gameCount());
        assertEquals(4, reader.quick(DreamResponse.STEP_ASK_OVERFLOW, "4", NUMBERS).gameCount());
        assertEquals(Intent.GAMES, quick(DreamResponse.STEP_ASK_OVERFLOW, "2판 할게요"));
    }

    @Test
    void 통계로_줄이기와_뺄_번호를_읽는다() {
        assertEquals(Intent.STATS, quick(DreamResponse.STEP_ASK_OVERFLOW, "통계로 6개만 골라 주세요"));
        Reading remove = reader.quick(DreamResponse.STEP_ASK_OVERFLOW, "7번이랑 9번 빼고 해주세요", NUMBERS);
        assertEquals(Intent.REMOVE, remove.intent());
        assertEquals(Set.of(7, 9), remove.remove());
        assertEquals(Set.of(12), reader.quick(DreamResponse.STEP_ASK_OVERFLOW, "12 제외", NUMBERS).remove());
        // 꿈에서 나오지 않은 번호는 뺄 대상이 아니다
        assertEquals(Intent.UNCLEAR, quick(DreamResponse.STEP_ASK_OVERFLOW, "40번 빼고"));
        assertEquals(Set.of(7), reader.quick(DreamResponse.STEP_ASK_OVERFLOW, "7번 40번 빼고", NUMBERS).remove());
        assertEquals(Intent.UNCLEAR, quick(DreamResponse.STEP_ASK_OVERFLOW, "음 글쎄요"));
    }

    @Test
    void 말이_비었거나_질문_중이_아니면_읽지_않는다() {
        assertEquals(Intent.UNCLEAR, quick(DreamResponse.STEP_CONFIRM, ""));
        assertEquals(Intent.UNCLEAR, quick(DreamResponse.STEP_CONFIRM, null));
        assertEquals(Intent.UNCLEAR, quick(null, "확정할게요"));
        assertEquals(Intent.UNCLEAR, quick(DreamResponse.STEP_TALK, "확정할게요"));
    }

    @Test
    void 짧은_말만_AI_에게_읽힌다() {
        assertTrue(reader.isShort("네"));
        assertTrue(reader.isShort("음 그러자!"));
        assertFalse(reader.isShort("어젯밤 꿈에서 다시 만난 친구가 네 번이나 나왔어요"));
        assertFalse(reader.isShort(null));
    }

    @Test
    void AI_에게_물어볼_때_질문과_사용자의_답을_함께_보낸다() {
        AiRequest request = reader.aiRequest(DreamResponse.STEP_ASK_OVERFLOW, "세 판만요");

        assertEquals(AiRole.CLASSIFIER, request.role());
        assertTrue(request.userPrompt().contains("세 판만요"));
        assertTrue(request.userPrompt().contains("몇 게임"));
    }

    @Test
    void AI_가_돌려준_글을_읽는다() {
        assertEquals(Intent.CONFIRM, reader.fromAi("{\"intent\":\"confirm\"}", NUMBERS).intent());
        assertEquals(Intent.REGENERATE, reader.fromAi("{\"intent\":\"regenerate\"}", NUMBERS).intent());
        assertEquals(Intent.YES, reader.fromAi("{\"intent\":\"yes\"}", NUMBERS).intent());
        assertEquals(Intent.NO, reader.fromAi("{\"intent\":\"no\"}", NUMBERS).intent());
        assertEquals(Intent.STATS, reader.fromAi("{\"intent\":\"stats\"}", NUMBERS).intent());
        // 앞뒤에 다른 글이 붙어도 JSON 부분만 읽는다
        Reading games = reader.fromAi("네 알겠습니다 {\"intent\":\"games\",\"gameCount\":3} 끝", NUMBERS);
        assertEquals(Intent.GAMES, games.intent());
        assertEquals(3, games.gameCount());
        Reading remove = reader.fromAi("{\"intent\":\"remove\",\"remove\":[7,40]}", NUMBERS);
        assertEquals(Intent.REMOVE, remove.intent());
        assertEquals(Set.of(7), remove.remove());      // 꿈에서 나온 번호만 인정한다
    }

    @Test
    void AI_가_이상한_글을_주면_못_알아들은_것으로_본다() {
        assertEquals(Intent.UNCLEAR, reader.fromAi(null, NUMBERS).intent());
        assertEquals(Intent.UNCLEAR, reader.fromAi("모르겠어요", NUMBERS).intent());
        assertEquals(Intent.UNCLEAR, reader.fromAi("{깨진 json}", NUMBERS).intent());
        assertEquals(Intent.UNCLEAR, reader.fromAi("{\"intent\":\"other\"}", NUMBERS).intent());
        assertEquals(Intent.UNCLEAR, reader.fromAi("{\"intent\":\"games\",\"gameCount\":0}", NUMBERS).intent());
        assertEquals(Intent.UNCLEAR, reader.fromAi("{\"intent\":\"remove\",\"remove\":[40]}", NUMBERS).intent());
        assertNull(reader.fromAi("{\"intent\":\"confirm\"}", NUMBERS).gameCount());
    }
}
