package com.fortunaball.analyst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fortunaball.analyst.domain.ParsedQuestion;
import com.fortunaball.analyst.domain.QuestionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestionParserTest {

    private final QuestionParser parser = new QuestionParser();

    private ParsedQuestion parse(String q) {
        return parser.parse(q, false);
    }

    @Test
    void 게임_수와_포함_제외_번호를_읽는다() {
        ParsedQuestion p = parse("3게임 만들어줘. 7하고 13 포함해서, 20은 빼고");
        assertEquals(QuestionType.NUMBER_GENERATION, p.type());
        assertEquals(3, p.gameCount());
        assertEquals(List.of(7, 13), p.include());
        assertEquals(List.of(20), p.exclude());
    }

    @Test
    void 넣지_마_같은_제외_표현은_포함으로_읽지_않는다() {
        ParsedQuestion p = parse("5 넣지 마 그리고 10 꼭 넣어서 번호 뽑아줘");
        assertEquals(List.of(5), p.exclude());
        assertEquals(List.of(10), p.include());
    }

    @Test
    void 들어가지_않게도_제외로_읽는다() {
        ParsedQuestion p = parse("44 45 안 들어가게 조합 만들어줘");
        assertEquals(List.of(44, 45), p.exclude());
        assertTrue(p.include().isEmpty());
    }

    @Test
    void 번호_생성_표현만_있어도_번호_생성이고_게임_수는_비어_있다() {
        ParsedQuestion p = parse("통계로 번호 하나 추천해줘");
        assertEquals(QuestionType.NUMBER_GENERATION, p.type());
        assertNull(p.gameCount());
    }

    @Test
    void 로또와_무관한_추천_요청은_번호_생성이_아니다() {
        ParsedQuestion p = parse("점심 메뉴 추천해줘");
        assertEquals(QuestionType.STAT_ANALYSIS, p.type());
        assertTrue(!p.hasLottoInput());
    }

    @Test
    void 발급코드가_있으면_발급_분석이다() {
        ParsedQuestion p = parse("ab3cd4ef5gh6 이 번호 어때?");
        assertEquals(QuestionType.ISSUE_ANALYSIS, p.type());
        assertEquals("AB3CD4EF5GH6", p.issueCode());
    }

    @Test
    void 헷갈리는_글자가_든_12자는_발급코드로_보지_않는다() {
        // I, L, O, 0, 1 은 발급코드에 쓰지 않는다
        assertNull(parse("ABCDEFGHIJKL 이건 뭐야").issueCode());
    }

    @Test
    void 번호_6개를_직접_적으면_번호_분석이다() {
        ParsedQuestion p = parse("3 14 22 35 38 44 이 조합 분석해줘");
        assertEquals(QuestionType.NUMBER_ANALYSIS, p.type());
        assertEquals(List.of(3, 14, 22, 35, 38, 44), p.givenNumbers());
    }

    @Test
    void 분석_같은_낱말의_첫_글자는_단위로_보지_않는다() {
        ParsedQuestion p = parse("3 14 22 35 38 44 분석해줘");
        assertEquals(QuestionType.NUMBER_ANALYSIS, p.type());
        assertEquals(List.of(3, 14, 22, 35, 38, 44), p.givenNumbers());
        assertTrue(parse("3시에 만날 건데 날씨 알려줘").givenNumbers().isEmpty());
    }

    @Test
    void 회차를_읽는다() {
        ParsedQuestion p = parse("1245회차 당첨번호 특징이 뭐야?");
        assertEquals(QuestionType.ROUND_ANALYSIS, p.type());
        assertEquals(1245, p.roundNo());
        assertEquals(List.of(), p.givenNumbers());
    }

    @Test
    void 최근_기간은_회차가_아니다() {
        ParsedQuestion p = parse("최근 20회 동안 자주 나온 번호 알려줘");
        assertEquals(QuestionType.STAT_ANALYSIS, p.type());
        assertEquals(20, p.periodRounds());
        assertNull(p.roundNo());
    }

    @Test
    void 등수나_시간_숫자는_번호로_읽지_않는다() {
        ParsedQuestion p = parse("1등 당첨이 3시에 나왔다는데 번호가 뭐야");
        assertTrue(p.include().isEmpty());
        assertTrue(p.exclude().isEmpty());
        assertTrue(p.givenNumbers().isEmpty());
    }

    @Test
    void 지도_질문이면_지도_유형이다() {
        ParsedQuestion p = parser.parse("1등 많이 나온 판매점 알려줘", true);
        assertEquals(QuestionType.MAP_STORE, p.type());
    }

    @Test
    void 범위_밖_숫자는_무시하고_빈_질문도_안전하다() {
        assertTrue(parse("99 포함해서 만들어줘").include().isEmpty());
        assertEquals(QuestionType.STAT_ANALYSIS, parse("").type());
        assertEquals(QuestionType.STAT_ANALYSIS, parser.parse(null, false).type());
    }

    @Test
    void 질문에_로또_입력이_있으면_hasLottoInput이_참이다() {
        assertTrue(parse("5게임 만들어줘").hasLottoInput());
        assertTrue(!parse("오늘 날씨 어때").hasLottoInput());
    }
}
