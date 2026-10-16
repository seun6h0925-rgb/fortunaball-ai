package com.fortunaball.draw.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fortunaball.draw.domain.DrawPrize;
import com.fortunaball.draw.domain.DrawRound;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 동행복권 회차 조회 응답을 받는 클래스.
 *   주소: https://www.dhlottery.co.kr/lt645/selectPstLt645Info.do?srchLtEpsd={회차}
 *   (2026-10-16 크롬에서 1245회로 확인한 응답 구조 기준)
 * 📚 배운 내용: DTO - 바깥(공식 사이트)에서 받은 데이터를 담는 그릇. 테이블 클래스(Entity)와 분리해서 둔다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 항목 이름(tm1WnNo, rnk1WnAmt ...)은 공식 응답의 이름 그대로여야 값이 채워진다. 한 글자라도 다르면 0 이 된다.
 *    사이트가 또 개편되면 여기부터 확인한다.
 *  - 공식 응답에는 우리가 안 쓰는 항목(판매액 등)이 많아서 @JsonIgnoreProperties 로 무시한다.
 *  - 없는 회차(아직 추첨 전)를 요청하면 data.list 가 비어 있을 수 있다 → hasData() 로 먼저 확인한다.
 *  - 추첨 직후에는 2~5등 값이 0 으로 오는 "집계 전" 상태가 있다고 한다 → isPrizeAggregated() 로 확인하고,
 *    집계 전이면 저장하지 않고 다음에 다시 받는다 (0원으로 저장되면 잘못된 데이터가 된다).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DrawApiResponse(String resultCode, String resultMessage, Data data) {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(List<Item> list) { }

    /** 한 회차의 정보 (공식 응답의 list 안 한 칸) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            int ltEpsd,          // 회차 번호
            String ltRflYmd,     // 추첨일 (예: 20261010)
            int tm1WnNo, int tm2WnNo, int tm3WnNo, int tm4WnNo, int tm5WnNo, int tm6WnNo,   // 당첨번호 6개
            int bnsWnNo,         // 보너스 번호
            int rnk1WnNope, long rnk1WnAmt,   // 1등 당첨자 수, 1인 당첨금
            int rnk2WnNope, long rnk2WnAmt,
            int rnk3WnNope, long rnk3WnAmt,
            int rnk4WnNope, long rnk4WnAmt,
            int rnk5WnNope, long rnk5WnAmt) {

        /** 2~5등 값이 채워졌는지. 5등 당첨자는 매 회차 수백만 명이라 0 이면 아직 집계 전이다. */
        public boolean isPrizeAggregated() {
            return rnk5WnNope > 0 && rnk4WnNope > 0;
        }

        /** 우리 테이블 draw_round 용 객체로 바꾼다 (번호 정렬·검증은 DrawRound 가 한다). */
        public DrawRound toDrawRound(String dataSource) {
            return new DrawRound(ltEpsd, LocalDate.parse(ltRflYmd, DATE),
                    List.of(tm1WnNo, tm2WnNo, tm3WnNo, tm4WnNo, tm5WnNo, tm6WnNo), bnsWnNo, dataSource);
        }

        /** 우리 테이블 draw_prize 용 객체 5개(1~5등)로 바꾼다. */
        public List<DrawPrize> toDrawPrizes() {
            List<DrawPrize> prizes = new ArrayList<>();
            prizes.add(new DrawPrize(ltEpsd, 1, rnk1WnAmt, rnk1WnNope));
            prizes.add(new DrawPrize(ltEpsd, 2, rnk2WnAmt, rnk2WnNope));
            prizes.add(new DrawPrize(ltEpsd, 3, rnk3WnAmt, rnk3WnNope));
            prizes.add(new DrawPrize(ltEpsd, 4, rnk4WnAmt, rnk4WnNope));
            prizes.add(new DrawPrize(ltEpsd, 5, rnk5WnAmt, rnk5WnNope));
            return prizes;
        }
    }

    /** 회차 정보가 하나라도 들어 있는지 */
    public boolean hasData() {
        return data != null && data.list() != null && !data.list().isEmpty();
    }

    /** 첫 번째 회차 정보 (hasData() 가 true 일 때만 호출) */
    public Item first() {
        return data.list().get(0);
    }
}
