package com.fortunaball.analyst.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fortunaball.analyst.service.AnalystSmallTalk.Kind;
import org.junit.jupiter.api.Test;

/** 인사·감사는 조금 변형돼도(요~ 하이, 정말 감사합니다!) 고정 문구로 알아본다. 로또 질문이 섞이면 알아보지 않는다. */
class AnalystSmallTalkFuzzyTest {

    private final AnalystSmallTalk talk = new AnalystSmallTalk();

    @Test
    void 변형된_인사도_인사로_본다() {
        assertThat(talk.detect("요~ 하이", false)).contains(Kind.GREETING);
        assertThat(talk.detect("안녕하세요 반갑습니다!", false)).contains(Kind.GREETING);
        assertThat(talk.detect("안녕 ㅎㅎ", false)).contains(Kind.GREETING);
        assertThat(talk.detect("와썹~", false)).contains(Kind.GREETING);
        assertThat(talk.detect("ㅎㅇ", false)).contains(Kind.GREETING);
        assertThat(talk.detect("방가방가", false)).contains(Kind.GREETING);
        assertThat(talk.detect("굿모닝!", false)).contains(Kind.GREETING);
    }

    @Test
    void 변형된_감사도_감사로_본다() {
        assertThat(talk.detect("정말 감사합니다!", false)).contains(Kind.THANKS);
        assertThat(talk.detect("고마워요 너무 도움됐어요", false)).contains(Kind.THANKS);
    }

    @Test
    void 질문이_섞이면_인사로_보지_않는다() {
        assertThat(talk.detect("하이 번호 만들어줘", false)).isEmpty();
        assertThat(talk.detect("안녕 이번주 1등 당첨금 알려줘", false)).isEmpty();
        assertThat(talk.detect("감사합니다 그럼 다음 회차는 어때요", false)).isEmpty();
    }

    @Test
    void 로또_입력이_있으면_인사로_보지_않는다() {
        assertThat(talk.detect("안녕 1245회", true)).isEmpty();
    }
}
