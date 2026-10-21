package com.fortunaball.dream.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fortunaball.analyst.service.AnalystSmallTalk;
import com.fortunaball.dream.domain.DreamKnowledge.MatchedSymbol;
import com.fortunaball.dream.domain.DreamKnowledge.PickedNumber;
import com.fortunaball.dream.domain.DreamKnowledge.SymbolNumber;
import com.fortunaball.dream.domain.DreamNumberOrigin;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DreamMessagesTest {

    private final DreamMessages messages = new DreamMessages();

    @Test
    void 같은_상황의_문구는_돌아가며_나온다() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            seen.add(messages.offTopic());
        }
        assertEquals(3, seen.size());
    }

    @Test
    void 인사_감사_사용법을_알아본다() {
        assertEquals(AnalystSmallTalk.Kind.GREETING, messages.detectSmallTalk("안녕하세요!").orElseThrow());
        assertEquals(AnalystSmallTalk.Kind.THANKS, messages.detectSmallTalk("고마워요").orElseThrow());
        assertEquals(AnalystSmallTalk.Kind.USAGE, messages.detectSmallTalk("사용법 알려줘").orElseThrow());
        assertTrue(messages.detectSmallTalk("돼지가 나오는 꿈을 꿨어요").isEmpty());
    }

    @Test
    void 사용법_문구에는_버튼이_붙는다() {
        assertTrue(messages.talk(AnalystSmallTalk.Kind.USAGE).openGuide());
        assertTrue(!messages.talk(AnalystSmallTalk.Kind.GREETING).openGuide());
    }

    @Test
    void 상징별_번호와_꿈_속_숫자를_한_줄로_만든다() {
        List<MatchedSymbol> symbols = List.of(new MatchedSymbol(1, "돼지"), new MatchedSymbol(2, "용"));
        List<List<SymbolNumber>> bySymbol = List.of(
                List.of(new SymbolNumber(1, 4, DreamNumberOrigin.SMORFIA), new SymbolNumber(1, 32, DreamNumberOrigin.CHARADA)),
                List.of(new SymbolNumber(2, 8, DreamNumberOrigin.SERVICE)));
        List<PickedNumber> base = List.of(
                new PickedNumber(4, DreamNumberOrigin.SMORFIA), new PickedNumber(8, DreamNumberOrigin.SERVICE),
                new PickedNumber(17, DreamNumberOrigin.DREAM_TEXT), new PickedNumber(32, DreamNumberOrigin.CHARADA));

        assertEquals("[돼지 → 4·32, 용 → 8, 꿈 속 숫자 → 17]", messages.symbolLine(symbols, bySymbol, base));
    }

    @Test
    void 꿈_속_숫자와_겹치는_상징_번호는_꿈_속_숫자로만_보여_준다() {
        List<MatchedSymbol> symbols = List.of(new MatchedSymbol(1, "돼지"));
        List<List<SymbolNumber>> bySymbol = List.of(List.of(new SymbolNumber(1, 17, DreamNumberOrigin.SERVICE)));
        List<PickedNumber> base = List.of(new PickedNumber(17, DreamNumberOrigin.DREAM_TEXT));

        assertEquals("[꿈 속 숫자 → 17]", messages.symbolLine(symbols, bySymbol, base));
    }

    @Test
    void 결과_첫마디의_자리표시는_실제_상징으로_바뀐다() {
        for (int i = 0; i < 3; i++) {
            String text = messages.openFilled("[돼지 → 4]");
            assertTrue(text.contains("[돼지 → 4]"));
            assertTrue(!text.contains(DreamMessages.SYMBOL_SLOT));
        }
        assertTrue(messages.confirmAskFilled().contains("확정하시겠어요"));
    }

    @Test
    void 번호가_6개를_넘을_때_문구에는_개수와_상징_번호가_들어가고_게임_수로_답하는_예시가_있다() {
        for (int i = 0; i < 2; i++) {
            String text = messages.overflow(9, "[돼지 → 4·32]");
            assertTrue(text.contains("9개"));
            assertTrue(text.contains("[돼지 → 4·32]"));
            assertTrue(text.contains("3게임") || text.contains("게임 수"));
            assertTrue(!text.contains(DreamMessages.SYMBOL_SLOT) && !text.contains("{n}"));
        }
    }

    @Test
    void 게임으로_나눈_뒤_문구에는_게임_수가_들어간다() {
        assertTrue(messages.multiGames(3).contains("3게임"));
        assertTrue(!messages.multiGames(3).contains("{n}"));
    }

    @Test
    void 확정과_다시_만들기_안내에는_횟수_사용이_분명히_적혀_있다() {
        assertTrue(messages.confirmAskFilled().contains("1회"));
        assertTrue(messages.askAfterFill().contains("1회"));
    }

    @Test
    void 답_칩은_말로_그대로_보낼_수_있는_문장이다() {
        assertEquals(List.of("확정할게요", "다시 만들어 주세요"), DreamMessages.REPLIES_CONFIRM);
        assertEquals(List.of("확정할게요"), DreamMessages.REPLIES_CONFIRM_ONLY);
        assertTrue(DreamMessages.REPLIES_OVERFLOW.get(0).contains("게임"));
    }
}
